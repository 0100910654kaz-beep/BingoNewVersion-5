package servlet;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class BingoGame implements Serializable {
    private static final long serialVersionUID = 1L;

    private String gameId;                             
    private List<Integer> drawnNumbers;                
    private List<PlayerResult> bingoPlayers;           
    private List<PlayerResult> reachPlayers;           
    private List<String> allPlayers;                   
    private Date expireTime;                           
    private Date lastBingoTime;                        
    private int anonymousCount = 0;                    

    private ConcurrentHashMap<String, List<List<String>>> playerCards = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, List<String>> playerWaitNumbers = new ConcurrentHashMap<>();

    public BingoGame(String gameId, int validDays) {
        this.gameId = gameId;
        this.drawnNumbers = new CopyOnWriteArrayList<>();
        this.bingoPlayers = new CopyOnWriteArrayList<>();
        this.reachPlayers = new CopyOnWriteArrayList<>();
        this.allPlayers = new CopyOnWriteArrayList<>();
        this.lastBingoTime = new Date(); 

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, validDays);
        this.expireTime = cal.getTime();
    }

    // 🔒【安全対策】一括クリア処理に鍵をかけ、リセット時のすれ違いを防ぐ
    public synchronized void clearGameDataOnly() {
        this.drawnNumbers.clear();
        this.bingoPlayers.clear();
        this.reachPlayers.clear();
        this.playerCards.clear();
        this.playerWaitNumbers.clear();
        this.lastBingoTime = new Date();
    }

    public synchronized String generateAnonymousName() {
        anonymousCount++;
        return "ゲスト" + anonymousCount;
    }

    // 🔒【安全対策】カードの登録に鍵をかけ、計算中の書き換えによるバグを完全防止
    public synchronized void setPlayerCard(String playerName, List<List<String>> card) {
        if (playerName == null || playerName.isEmpty() || card == null) return;
        playerCards.put(playerName, card);
    }

    public synchronized List<List<String>> getPlayerCard(String playerName) {
        if (playerName == null) return null;
        return playerCards.get(playerName);
    }

    // 🔒【安全対策】全員のリーチ・ビンゴ判定計算に鍵をかけ、順番に確実に処理する
    public synchronized void updateAllPlayersStatus() {
        List<PlayerResult> currentBingo = new ArrayList<>();
        List<PlayerResult> currentReach = new ArrayList<>();
        ConcurrentHashMap<String, List<String>> newWaitNumbers = new ConcurrentHashMap<>();

        for (String name : allPlayers) {
            List<List<String>> card = playerCards.get(name);
            if (card == null) continue;

            boolean hasBingo = false;
            boolean hasReach = false;
            List<String> waitsForThisPlayer = new ArrayList<>();

            // 横ラインチェック
            for (int r = 0; r < 5; r++) {
                int missCount = 0;
                String lastMissNum = "";
                for (int c = 0; c < 5; c++) {
                    String num = card.get(r).get(c);
                    if (num.equals("0")) continue;
                    if (!drawnNumbers.contains(Integer.parseInt(num))) {
                        missCount++;
                        lastMissNum = num;
                    }
                }
                if (missCount == 0) hasBingo = true;
                if (missCount == 1) { hasReach = true; if (!waitsForThisPlayer.contains(lastMissNum)) waitsForThisPlayer.add(lastMissNum); }
            }

            // 縦ラインチェック
            for (int c = 0; c < 5; c++) {
                int missCount = 0;
                String lastMissNum = "";
                for (int r = 0; r < 5; r++) {
                    String num = card.get(r).get(c);
                    if (num.equals("0")) continue;
                    if (!drawnNumbers.contains(Integer.parseInt(num))) {
                        missCount++;
                        lastMissNum = num;
                    }
                }
                if (missCount == 0) hasBingo = true;
                if (missCount == 1) { hasReach = true; if (!waitsForThisPlayer.contains(lastMissNum)) waitsForThisPlayer.add(lastMissNum); }
            }

            // ななめ（左上から右下）
            {
                int missCount = 0;
                String lastMissNum = "";
                for (int i = 0; i < 5; i++) {
                    String num = card.get(i).get(i);
                    if (num.equals("0")) continue;
                    if (!drawnNumbers.contains(Integer.parseInt(num))) {
                        missCount++;
                        lastMissNum = num;
                    }
                }
                if (missCount == 0) hasBingo = true;
                if (missCount == 1) { hasReach = true; if (!waitsForThisPlayer.contains(lastMissNum)) waitsForThisPlayer.add(lastMissNum); }
            }

            // ななめ（右上から左下）
            {
                int missCount = 0;
                String lastMissNum = "";
                for (int i = 0; i < 5; i++) {
                    String num = card.get(i).get(4 - i);
                    if (num.equals("0")) continue;
                    if (!drawnNumbers.contains(Integer.parseInt(num))) {
                        missCount++;
                        lastMissNum = num;
                    }
                }
                if (missCount == 0) hasBingo = true;
                if (missCount == 1) { hasReach = true; if (!waitsForThisPlayer.contains(lastMissNum)) waitsForThisPlayer.add(lastMissNum); }
            }

            if (hasBingo) {
                int lastNum = drawnNumbers.isEmpty() ? 0 : drawnNumbers.get(drawnNumbers.size() - 1);
                currentBingo.add(new PlayerResult(name, new Date(), lastNum));
            } else if (hasReach) {
                currentReach.add(new PlayerResult(name, new Date(), 0));
                newWaitNumbers.put(name, waitsForThisPlayer);
            }
        }

        // ビンゴリストの更新（新しくビンゴした人を先頭にする）
        for (PlayerResult newB : currentBingo) {
            boolean exists = false;
            for (PlayerResult oldB : bingoPlayers) {
                if (oldB.getPlayerName().equals(newB.getPlayerName())) { exists = true; break; }
            }
            if (!exists) {
                bingoPlayers.add(0, newB);
                lastBingoTime = new Date();
            }
        }

        // リーチリストの更新
        reachPlayers.clear();
        for (PlayerResult newR : currentReach) {
            boolean inBingo = false;
            for (PlayerResult b : bingoPlayers) {
                if (b.getPlayerName().equals(newR.getPlayerName())) { inBingo = true; break; }
            }
            if (!inBingo) {
                reachPlayers.add(newR);
            }
        }

        playerWaitNumbers.clear();
        playerWaitNumbers.putAll(newWaitNumbers);
    }

    public synchronized List<String> getWaitNumbers(String name) {
        return playerWaitNumbers.getOrDefault(name, new ArrayList<>());
    }

    public boolean isExpired() { return new Date().after(this.expireTime); }
    
    public boolean isPast2HoursFromLastBingo() {
        if (drawnNumbers.isEmpty() && bingoPlayers.isEmpty()) return false;
        long twoHoursInMilliseconds = 2L * 60 * 60 * 1000;
        long timePassed = new Date().getTime() - lastBingoTime.getTime();
        return timePassed > twoHoursInMilliseconds;
    }

    public String getGameId() { return gameId; }
    public List<Integer> getDrawnNumbers() { return drawnNumbers; }
    public List<PlayerResult> getBingoPlayers() { return bingoPlayers; }
    public List<PlayerResult> getReachPlayers() { return reachPlayers; }
    public int getPlayerCount() { return playerCards.size(); }
    public List<String> getAllPlayers() { return allPlayers; }
}
