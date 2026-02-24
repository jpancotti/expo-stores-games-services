package expo.modules.storesgamesservices

import android.util.Base64
import com.google.android.gms.games.AchievementsClient
import com.google.android.gms.games.GamesSignInClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.Player
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.achievement.Achievement
import com.google.android.gms.games.snapshot.SnapshotMetadata
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ExpoStoresGamesServicesModule : Module() {
  private var pendingPromise: Promise? = null
  private val RC_LEADERBOARD_UI: Int = 9004
  private val RC_ACHIEVEMENTS_UI: Int = 9005

  private fun snapshotMetadataMap(metadata: SnapshotMetadata): Map<String, Any> {
    return mapOf(
      "name" to metadata.uniqueName,
      "modificationDate" to metadata.lastModifiedTimestamp
    )
  }

  override fun definition() = ModuleDefinition {
    Name("ExpoStoresGamesServices")

    OnActivityResult { activity, payload ->
      val requestCode = payload.requestCode
      val resultCode = payload.resultCode
      val data = payload.data

      when(requestCode) {
        RC_LEADERBOARD_UI -> {
          pendingPromise?.resolve()
          pendingPromise = null
        }
        RC_ACHIEVEMENTS_UI -> {
          pendingPromise?.resolve()
          pendingPromise = null
        }
      }
    }

    AsyncFunction("isAuthenticated") { promise: Promise ->
      val activity = appContext.currentActivity
      if (activity == null) {
        promise.reject("NO_ACTIVITY", "No current activity", null)
        return@AsyncFunction
      }

      val gamesSignInClient: GamesSignInClient = PlayGames.getGamesSignInClient(activity)

      gamesSignInClient.isAuthenticated().addOnCompleteListener { isAuthenticatedTask ->
        if (isAuthenticatedTask.isSuccessful) {
          val isAuthenticated = isAuthenticatedTask.result.isAuthenticated
          promise.resolve(isAuthenticated)
        } else {
          promise.reject("AUTHENTICATION_ERROR", "Failed to check authentication status", null)
        }
      }
    }

    AsyncFunction("signIn") { promise: Promise ->
      val activity = appContext.currentActivity
      if (activity == null) {
        promise.reject("NO_ACTIVITY", "No current activity", null)
        return@AsyncFunction
      }

      val gamesSignInClient: GamesSignInClient = PlayGames.getGamesSignInClient(activity)

      gamesSignInClient.signIn().addOnCompleteListener { signInTask ->
        if (signInTask.isSuccessful) {
          // Get player information after successful sign in
          val playersClient = PlayGames.getPlayersClient(activity)
          playersClient.currentPlayer.addOnCompleteListener { playerTask ->
            if (playerTask.isSuccessful) {
              val player: Player = playerTask.result
              val userInfo = mapOf(
                "playerID" to player.playerId,
                "alias" to "",
                "displayName" to player.displayName
              )
              promise.resolve(userInfo)
            } else {
              promise.reject("GET_PLAYER_INFO_FAILED", "Failed to get player information", null)
            }
          }
        } else {
          promise.reject("SIGN_IN_FAILED", "Failed to sign in", null)
        }
      }
    }

    AsyncFunction("showLeaderboard") { leaderboardId: String, timeSpan: Int, promise: Promise ->
      val activity = appContext.currentActivity
      if (activity == null) {
        promise.reject("NO_ACTIVITY", "No current activity", null)
        return@AsyncFunction
      }

      PlayGames.getLeaderboardsClient(activity)
        .getLeaderboardIntent(leaderboardId, timeSpan)
        .addOnSuccessListener { intent ->
          pendingPromise = promise
          activity.startActivityForResult(intent, RC_LEADERBOARD_UI)
        }
        .addOnFailureListener { exception: Exception ->
          promise.reject("SHOW_LEADERBOARD_FAILED", "Failed to show leaderboard: ${exception.message ?: exception.localizedMessage ?: "Unknown error"}", exception)
        }
    }


    AsyncFunction("submitScore") { score: Long, leaderboardID: String, promise: Promise ->
      val activity = appContext.currentActivity
      if (activity == null) {
        promise.reject("NO_ACTIVITY", "No current activity", null)
        return@AsyncFunction
      }

      PlayGames.getLeaderboardsClient(activity)
        .submitScore(leaderboardID, score);

      promise.resolve()
    }

    AsyncFunction("getUserScore") { leaderboardID: String, timeSpan: Int, promise: Promise ->
      val activity = appContext.currentActivity
      if (activity == null) {
        promise.reject("NO_ACTIVITY", "No current activity", null)
        return@AsyncFunction
      }

      PlayGames.getLeaderboardsClient(activity)
        .loadCurrentPlayerLeaderboardScore(leaderboardID, timeSpan, 0)
        .addOnSuccessListener { intent ->
          val scoreData = intent.get()

          if (scoreData != null) {
            val result = mapOf(
              "score" to scoreData.rawScore,
              "rank" to scoreData.rank,
              "formattedScore" to scoreData.displayScore,
              "context" to 0L,
            )
            promise.resolve(result)
          } else {
            promise.resolve(null)
          }
        }
        .addOnFailureListener { exception: Exception ->
          promise.reject("GET_USER_SCORE_FAILED", "Failed to get user score: ${exception.message ?: exception.localizedMessage ?: "Unknown error"}", exception)
        }
    }

    AsyncFunction("showAchievements") { promise: Promise ->
      val activity = appContext.currentActivity
      if (activity == null) {
        promise.reject("NO_ACTIVITY", "No current activity", null)
        return@AsyncFunction
      }

      PlayGames.getAchievementsClient(activity)
        .achievementsIntent
        .addOnSuccessListener { intent ->
          pendingPromise = promise
          activity.startActivityForResult(intent, RC_ACHIEVEMENTS_UI)
        }
        .addOnFailureListener { exception: Exception ->
          promise.reject("SHOW_ACHIEVEMENTS_FAILED", "Failed to show achievements: ${exception.message ?: exception.localizedMessage ?: "Unknown error"}", exception)
        }
    }

    AsyncFunction("unlockAchievement") { achievementId: String, promise: Promise ->
      val activity = appContext.currentActivity
      if (activity == null) {
        promise.reject("NO_ACTIVITY", "No current activity", null)
        return@AsyncFunction
      }

      val achievementsClient: AchievementsClient = PlayGames.getAchievementsClient(activity)
      achievementsClient.unlock(achievementId)
      promise.resolve()
    }

    AsyncFunction("incrementAchievement") { achievementId: String, stepsIncrement: Int, totalSteps: Int, promise: Promise ->
      val activity = appContext.currentActivity
      if (activity == null) {
        promise.reject("NO_ACTIVITY", "No current activity", null)
        return@AsyncFunction
      }

      val achievementsClient: AchievementsClient = PlayGames.getAchievementsClient(activity)
      // Android expects the number of steps to increment, not the percentage
      achievementsClient.increment(achievementId, stepsIncrement)
      promise.resolve()
    }

    AsyncFunction("getAchievements") { promise: Promise ->
      val activity = appContext.currentActivity
      if (activity == null) {
        promise.reject("NO_ACTIVITY", "No current activity", null)
        return@AsyncFunction
      }

      val achievementsClient: AchievementsClient = PlayGames.getAchievementsClient(activity)
      achievementsClient.load(false)
        .addOnSuccessListener { result ->
          val achievements = result.get()
          val achievementsList = mutableListOf<Map<String, Any>>()

          if (achievements != null) {
            for (achievement in achievements) {
              val achievementData = mutableMapOf<String, Any>(
                "id" to achievement.achievementId,
                "name" to achievement.name,
                "description" to achievement.description,
                "unlocked" to (achievement.state == Achievement.STATE_UNLOCKED)
              )

              if (achievement.state == Achievement.STATE_UNLOCKED) {
                // Google Play Games API doesn't provide unlockedTimestamp directly
                // Using current time as fallback (achievements are typically unlocked recently)
                achievementData["unlockedAt"] = System.currentTimeMillis()
              }

              if (achievement.type == Achievement.TYPE_INCREMENTAL) {
                achievementData["progress"] = achievement.currentSteps
                achievementData["totalSteps"] = achievement.totalSteps
              }

              achievementsList.add(achievementData)
            }
          }

          promise.resolve(achievementsList)
        }
        .addOnFailureListener { exception: Exception ->
          promise.reject("GET_ACHIEVEMENTS_FAILED", "Failed to get achievements: ${exception.message ?: exception.localizedMessage ?: "Unknown error"}", exception)
        }
    }

    AsyncFunction("saveGameData") { data: String, name: String, promise: Promise ->
      val activity = appContext.currentActivity
      if (activity == null) {
        promise.reject("NO_ACTIVITY", "No current activity", null)
        return@AsyncFunction
      }

      val decodedBytes = try {
        Base64.decode(data, Base64.DEFAULT)
      } catch (exception: IllegalArgumentException) {
        promise.reject("INVALID_BASE64", "Data must be a valid base64 string", exception)
        return@AsyncFunction
      }

      val snapshotsClient = PlayGames.getSnapshotsClient(activity)
      snapshotsClient
        .open(name, true, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
        .addOnSuccessListener { openResult ->
          val snapshot = openResult.data
          if (snapshot == null) {
            promise.reject("SAVE_GAME_FAILED", "Failed to open snapshot for save", null)
            return@addOnSuccessListener
          }

          try {
            snapshot.snapshotContents.writeBytes(decodedBytes)
          } catch (exception: Exception) {
            promise.reject("SAVE_GAME_FAILED", "Failed to write snapshot data", exception)
            return@addOnSuccessListener
          }

          val metadataChange = SnapshotMetadataChange.Builder()
            .setDescription("Saved game data")
            .build()

          snapshotsClient
            .commitAndClose(snapshot, metadataChange)
            .addOnSuccessListener { metadata ->
              promise.resolve(snapshotMetadataMap(metadata))
            }
            .addOnFailureListener { exception: Exception ->
              promise.reject("SAVE_GAME_FAILED", "Failed to commit saved game: ${exception.message ?: exception.localizedMessage ?: "Unknown error"}", exception)
            }
        }
        .addOnFailureListener { exception: Exception ->
          promise.reject("SAVE_GAME_FAILED", "Failed to open saved game: ${exception.message ?: exception.localizedMessage ?: "Unknown error"}", exception)
        }
    }

    AsyncFunction("fetchSavedGames") { promise: Promise ->
      val activity = appContext.currentActivity
      if (activity == null) {
        promise.reject("NO_ACTIVITY", "No current activity", null)
        return@AsyncFunction
      }

      val snapshotsClient = PlayGames.getSnapshotsClient(activity)
      snapshotsClient.load(false)
        .addOnSuccessListener { result ->
          val metadataBuffer = result.get()
          if (metadataBuffer == null) {
            promise.resolve(emptyList<Map<String, Any>>())
            return@addOnSuccessListener
          }
          val saves = mutableListOf<Map<String, Any>>()

          try {
            for (i in 0 until metadataBuffer.count) {
              val metadata = metadataBuffer.get(i)
              if (metadata == null) continue
              saves.add(snapshotMetadataMap(metadata))
            }
          } finally {
            metadataBuffer.release()
          }

          val sorted = saves.sortedByDescending {
            (it["modificationDate"] as? Long) ?: 0L
          }
          promise.resolve(sorted)
        }
        .addOnFailureListener { exception: Exception ->
          promise.reject("FETCH_SAVED_GAMES_FAILED", "Failed to fetch saved games: ${exception.message ?: exception.localizedMessage ?: "Unknown error"}", exception)
        }
    }

    AsyncFunction("loadGameData") { name: String, promise: Promise ->
      val activity = appContext.currentActivity
      if (activity == null) {
        promise.reject("NO_ACTIVITY", "No current activity", null)
        return@AsyncFunction
      }

      val snapshotsClient = PlayGames.getSnapshotsClient(activity)
      snapshotsClient
        .open(name, false, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
        .addOnSuccessListener { openResult ->
          val snapshot = openResult.data
          if (snapshot == null) {
            promise.resolve(null)
            return@addOnSuccessListener
          }

          val metadata = snapshot.metadata
          val encoded = try {
            val bytes = snapshot.snapshotContents.readFully()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
          } catch (exception: Exception) {
            promise.reject("LOAD_GAME_DATA_FAILED", "Failed to read snapshot data", exception)
            return@addOnSuccessListener
          }

          snapshotsClient
            .discardAndClose(snapshot)
            .addOnSuccessListener {
              promise.resolve(
                mapOf(
                  "name" to metadata.uniqueName,
                  "modificationDate" to metadata.lastModifiedTimestamp,
                  "data" to encoded
                )
              )
            }
            .addOnFailureListener { exception: Exception ->
              promise.reject("LOAD_GAME_DATA_FAILED", "Failed to close snapshot after load: ${exception.message ?: exception.localizedMessage ?: "Unknown error"}", exception)
            }
        }
        .addOnFailureListener { exception: Exception ->
          promise.reject("LOAD_GAME_DATA_FAILED", "Failed to open saved game: ${exception.message ?: exception.localizedMessage ?: "Unknown error"}", exception)
        }
    }

    AsyncFunction("deleteSavedGames") { name: String, promise: Promise ->
      val activity = appContext.currentActivity
      if (activity == null) {
        promise.reject("NO_ACTIVITY", "No current activity", null)
        return@AsyncFunction
      }

      val snapshotsClient = PlayGames.getSnapshotsClient(activity)
      snapshotsClient
        .open(name, false, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
        .addOnSuccessListener { openResult ->
          val snapshot = openResult.data
          if (snapshot == null) {
            promise.resolve()
            return@addOnSuccessListener
          }

          snapshotsClient
            .delete(snapshot.metadata)
            .addOnSuccessListener {
              promise.resolve()
            }
            .addOnFailureListener { exception: Exception ->
              promise.reject("DELETE_SAVED_GAMES_FAILED", "Failed to delete saved game: ${exception.message ?: exception.localizedMessage ?: "Unknown error"}", exception)
            }
        }
        .addOnFailureListener { exception: Exception ->
          promise.reject("DELETE_SAVED_GAMES_FAILED", "Failed to open saved game for delete: ${exception.message ?: exception.localizedMessage ?: "Unknown error"}", exception)
        }
    }
  }
}
