import { NativeModule, requireNativeModule } from "expo";
import { UserScore, UserInfo, Achievement, SavedGame, SavedGameMetadata } from "./types";
import { TimeSpan } from "./constants";

declare class ExpoStoresGamesServicesModule extends NativeModule {
  isAuthenticated(): Promise<boolean>;
  signIn(): Promise<UserInfo>;
  showLeaderboard(leaderboardId: string, timeSpan?: TimeSpan): Promise<void>;
  submitScore(score: number, leaderboardId: string): Promise<void>;
  getUserScore(leaderboardId: string, timeSpan?: TimeSpan): Promise<UserScore | null>;
  showAchievements(): Promise<void>;
  unlockAchievement(achievementId: string): Promise<void>;
  incrementAchievement(achievementId: string, stepsIncrement: number, totalSteps: number): Promise<void>;
  getAchievements(): Promise<Achievement[]>;
  saveGameData(data: string, name: string): Promise<SavedGameMetadata>;
  fetchSavedGames(): Promise<SavedGameMetadata[]>;
  loadGameData(name: string): Promise<SavedGame | null>;
  deleteSavedGames(name: string): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoStoresGamesServicesModule>(
  "ExpoStoresGamesServices"
);
