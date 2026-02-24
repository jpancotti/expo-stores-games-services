import ExpoModulesCore
import Foundation
import GameKit

public class ExpoStoresGamesServicesModule:  Module {
    private func savedGameMetadataMap(_ game: GKSavedGame) -> [String: Any] {
        return [
            "name": game.name,
            "modificationDate": Int(game.modificationDate.timeIntervalSince1970 * 1000),
            "deviceName": game.deviceName
        ]
    }

    private func savedGameDataMap(_ game: GKSavedGame, data: Data) -> [String: Any] {
        return [
            "name": game.name,
            "modificationDate": Int(game.modificationDate.timeIntervalSince1970 * 1000),
            "deviceName": game.deviceName,
            "data": data.base64EncodedString()
        ]
    }

    private func selectMostRecent(_ games: [GKSavedGame]) -> GKSavedGame? {
        return games.max { left, right in
            left.modificationDate < right.modificationDate
        }
    }

    private func resolveNameConflictsIfNeeded(_ games: [GKSavedGame]) async throws -> [GKSavedGame] {
        let grouped = Dictionary(grouping: games, by: { $0.name })
        var hadConflicts = false

        for (_, group) in grouped where group.count > 1 {
            hadConflicts = true

            guard let preferred = selectMostRecent(group) else {
                continue
            }

            let preferredData = try await preferred.loadData()
            _ = try await GKLocalPlayer.local.resolveConflictingSavedGames(group, with: preferredData)
        }

        if hadConflicts {
            return try await GKLocalPlayer.local.fetchSavedGames()
        }

        return games
    }

    public func definition() -> ModuleDefinition {
        Name("ExpoStoresGamesServices")

        AsyncFunction("isAuthenticated") { () async -> Bool in
            return GKLocalPlayer.local.isAuthenticated
        }
        
        AsyncFunction("signIn") { () async throws -> [String: String] in
            let localPlayer = GKLocalPlayer.local
            
            return try await withCheckedThrowingContinuation { continuation in
                var hasResumed = false
                
                localPlayer.authenticateHandler = { viewController, error in
                    guard !hasResumed else { return }

                    if let error = error  {
                        hasResumed = true
                        continuation.resume(throwing: error)
                        return
                    }
                    
                    if let vc = viewController {
                        Task { @MainActor in
                            if let rootVC = UIApplication.shared.delegate?.window??.rootViewController {
                                rootVC.present(vc, animated: true, completion: nil)
                            } else {
                                print("No root view controller available")
                            }
                        }
                        return
                    }
                    
                    if localPlayer.isAuthenticated {
                        hasResumed = true
                        continuation.resume(returning: [
                            "playerID": localPlayer.gamePlayerID,
                            "alias": localPlayer.alias,
                            "displayName": localPlayer.displayName
                        ])
                    } else {
                        hasResumed = true
                        continuation.resume(throwing: NSError(domain: "GameCenter", code: 401, userInfo: [
                            NSLocalizedDescriptionKey: "User not authenticated"
                        ]))
                    }
                }
            }
        }
        
        AsyncFunction("showLeaderboard") { (leaderboardID: String, timeSpan: Int) async throws -> [String: Any] in
            await MainActor.run {
                let viewController = GKGameCenterViewController(
                                leaderboardID: leaderboardID,
                                playerScope: .global,
                                timeScope: GKLeaderboard.TimeScope(rawValue: timeSpan) ?? .allTime
                )
                viewController.gameCenterDelegate = GameCenterDelegate.shared
                
                if let rootVC = UIApplication.shared.delegate?.window??.rootViewController {
                    rootVC.present(viewController, animated: true, completion: nil)
                } else {
                    print("No root view controller available")
                }
            }
            
            return ["status": "shown"]
        }
        
        AsyncFunction("submitScore") { (score: Int, leaderboardID: String) async throws -> [String: Any] in
            try await GKLeaderboard.submitScore(
                score,
                context: 0,
                player: GKLocalPlayer.local,
                leaderboardIDs: [leaderboardID]
            )
            return ["status": "success"]
        }
        
        AsyncFunction("getUserScore") { (leaderboardID: String, timeSpan: Int) async throws -> [String: Any]? in
            let leaderboards = try await GKLeaderboard.loadLeaderboards(IDs: [leaderboardID])
            
            guard let leaderboard = leaderboards.first else {
                throw NSError(domain: "GameCenter", code: 404, userInfo: [
                    NSLocalizedDescriptionKey: "Leaderboard not found"
                ])
            }
            
            let (entry, _) = try await leaderboard.loadEntries(
                for: [GKLocalPlayer.local],
                timeScope: GKLeaderboard.TimeScope(rawValue: timeSpan) ?? .allTime
            )
            
            if let entry = entry {
                return [
                    "score": entry.score,
                    "rank": entry.rank,
                    "formattedScore": entry.formattedScore,
                    "context": entry.context
                ]
            } else {
                return nil  // No score
            }
        }
        
        AsyncFunction("showAchievements") { () async throws -> [String: Any] in
            await MainActor.run {
                let viewController = GKGameCenterViewController(state: .achievements)
                viewController.gameCenterDelegate = GameCenterDelegate.shared
                
                if let rootVC = UIApplication.shared.delegate?.window??.rootViewController {
                    rootVC.present(viewController, animated: true, completion: nil)
                } else {
                    print("No root view controller available")
                }
            }
            
            return ["status": "shown"]
        }
        
        AsyncFunction("unlockAchievement") { (achievementID: String) async throws -> [String: Any] in
            let achievement = GKAchievement(identifier: achievementID)
            achievement.percentComplete = 100.0
            achievement.showsCompletionBanner = true
            
            try await GKAchievement.report([achievement])
            
            return ["status": "unlocked"]
        }
        
        AsyncFunction("incrementAchievement") { (achievementID: String, stepsIncrement: Int, totalSteps: Int) async throws -> [String: Any] in
            // Load existing achievements to get current progress
            let achievements = try await GKAchievement.loadAchievements()
            var achievement: GKAchievement? = achievements.first { $0.identifier == achievementID }
            
            // If achievement not found, create a new one
            if achievement == nil {
                achievement = GKAchievement(identifier: achievementID)
            }
            
            guard let achievement = achievement else {
                throw NSError(domain: "GameCenter", code: 500, userInfo: [
                    NSLocalizedDescriptionKey: "Failed to create or load achievement"
                ])
            }
            
            // Calculate the percent increment based on steps
            // percentIncrement = (stepsIncrement / totalSteps) * 100
            let percentIncrement = (Double(stepsIncrement) / Double(totalSteps)) * 100.0
            
            let currentProgress = achievement.percentComplete
            
            // Don't increment if already at 100%
            guard currentProgress < 100.0 else {
                return [
                    "status": "already_completed",
                    "progress": 100.0
                ]
            }
            
            let newProgress = min(100.0, currentProgress + percentIncrement)
            achievement.percentComplete = newProgress
            
            // Show completion banner only when reaching 100%
            if newProgress >= 100.0 {
                achievement.showsCompletionBanner = true
            }
            
            try await GKAchievement.report([achievement])
            
            return [
                "status": "incremented",
                "newProgress": newProgress
            ]
        }
        
        AsyncFunction("getAchievements") { () async throws -> [[String: Any]] in
            let achievements = try await GKAchievement.loadAchievements()
            let achievementDescriptions = try await GKAchievementDescription.loadAchievementDescriptions()
            
            var result: [[String: Any]] = []
            
            // Create a dictionary of achievement descriptions by ID for quick lookup
            var descriptionsDict: [String: GKAchievementDescription] = [:]
            for desc in achievementDescriptions {
                descriptionsDict[desc.identifier] = desc
            }
            
            // Process unlocked achievements
            for achievement in achievements {
                let desc = descriptionsDict[achievement.identifier]
                var achievementData: [String: Any] = [
                    "id": achievement.identifier,
                    "name": desc?.title ?? achievement.identifier,
                    "description": desc?.achievedDescription ?? desc?.unachievedDescription ?? "",
                    "unlocked": achievement.isCompleted,
                ]
                
                if achievement.isCompleted {
                    let completedDate = achievement.lastReportedDate
                    achievementData["unlockedAt"] = Int(completedDate.timeIntervalSince1970 * 1000)
                }
                
                if !achievement.isCompleted && achievement.percentComplete > 0 {
                    achievementData["progress"] = Int(achievement.percentComplete)
                }
                
                result.append(achievementData)
            }
            
            // Add achievements that are defined but not yet unlocked
            for desc in achievementDescriptions {
                let isAlreadyIncluded = achievements.contains { $0.identifier == desc.identifier }
                if !isAlreadyIncluded {
                    result.append([
                        "id": desc.identifier,
                        "name": desc.title,
                        "description": desc.unachievedDescription,
                        "unlocked": false
                    ])
                }
            }
            
            return result
        }

        AsyncFunction("saveGameData") { (data: String, name: String) async throws -> [String: Any] in
            guard let decoded = Data(base64Encoded: data, options: .ignoreUnknownCharacters) else {
                throw NSError(domain: "GameCenter", code: 400, userInfo: [
                    NSLocalizedDescriptionKey: "Invalid base64 game data"
                ])
            }

            try await GKLocalPlayer.local.saveGameData(decoded, withName: name)
            let games = try await GKLocalPlayer.local.fetchSavedGames()
            let resolvedGames = try await resolveNameConflictsIfNeeded(games)
            let named = resolvedGames.filter { $0.name == name }

            guard let preferred = selectMostRecent(named) else {
                throw NSError(domain: "GameCenter", code: 404, userInfo: [
                    NSLocalizedDescriptionKey: "Saved game not found after save"
                ])
            }

            return savedGameMetadataMap(preferred)
        }

        AsyncFunction("fetchSavedGames") { () async throws -> [[String: Any]] in
            let games = try await GKLocalPlayer.local.fetchSavedGames()
            let resolvedGames = try await resolveNameConflictsIfNeeded(games)

            return resolvedGames
                .sorted { $0.modificationDate > $1.modificationDate }
                .map { savedGameMetadataMap($0) }
        }

        AsyncFunction("loadGameData") { (name: String) async throws -> [String: Any]? in
            let games = try await GKLocalPlayer.local.fetchSavedGames()
            let resolvedGames = try await resolveNameConflictsIfNeeded(games)
            let named = resolvedGames.filter { $0.name == name }

            guard let preferred = selectMostRecent(named) else {
                return nil
            }

            let data = try await preferred.loadData()
            return savedGameDataMap(preferred, data: data)
        }

        AsyncFunction("deleteSavedGames") { (name: String) async throws -> Void in
            try await GKLocalPlayer.local.deleteSavedGames(withName: name)
        }
    }
}

// MARK: - Game Center Delegate

class GameCenterDelegate: NSObject, GKGameCenterControllerDelegate {
    static let shared = GameCenterDelegate()
    
    func gameCenterViewControllerDidFinish(_ gameCenterViewController: GKGameCenterViewController) {
        gameCenterViewController.dismiss(animated: true, completion: nil)
    }
}
