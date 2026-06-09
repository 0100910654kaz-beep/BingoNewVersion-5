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

    public synchronized List<List<String>> getPlayerCard(String name) {
        if (name == null) return null;
        return playerCards.get(name);
    }

    public synchronized void setPlayerCard(String name, List<List<String>> card) {
        if (name == null || card == null) return;
        playerCards.put(name, card);
        if (!allPlayers.contains(name)) {
            allPlayers.add(name);
        }
    }

    public synchronized void drawNumber() {
        if (drawnNumbers.size() >= 75) return;
        
        int nextNum;
        do {
            nextNum = (int)(Math.random() * 75) + 1;
        } while (drawnNumbers.contains(nextNum));
        
        drawnNumbers.add(nextNum);

        // 新しい数字が出たので、全登録プレイヤーの状態を一斉に再計算
        List<PlayerResult> currentBingo = new ArrayList<>();
        List<PlayerResult> currentReach = new ArrayList<>();
        ConcurrentHashMap<String, List<String>> newWaitNumbers = new ConcurrentHashMap<>();

        for (String pName : allPlayers) {
            List<List<String>> card = playerCards.get(pName);
            if (card == null) continue;

            boolean[][] hits = new boolean[5][5];
            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 5; c++) {
                    String numStr = card.get(r).get(c);
                    int num = Integer.parseInt(numStr);
                    if (num == 0 || drawnNumbers.contains(num)) {
                        hits[r][c] = true;
                    }
                }
            }

            int bingoLines = 0;
            List<String> waitNumsForThisPlayer = new ArrayList<>();

            // 横のチェック
            for (int r = 0; r < 5; r++) {
                int missingCount = 0;
                String lastMissingNum = "";
                for (int c = 0; c < 5; c++) {
                    if (!hits[r][c]) {
                        missingCount++;
                        lastMissingNum = card.get(r).get(c);
                    }
                }
                if (missingCount == 0) bingoLines++;
                if (missingCount == 1) {
                    if (!waitNumsForThisPlayer.contains(lastMissingNum)) waitNumsForThisPlayer.add(lastMissingNum);
                }
            }

            // 縦のチェック
            for (int c = 0; c < 5; c++) {
                int missingCount = 0;
                String lastMissingNum = "";
                for (int r = 0; r < 5; r++) {
                    if (!hits[r][c]) {
                        missingCount++;
                        lastMissingNum = card.get(r).get(c);
                    }
                }
                if (missingCount == 0) bingoLines++;
                if (missingCount == 1) {
                    if (!waitNumsForThisPlayer.contains(lastMissingNum)) waitNumsForThisPlayer.add(lastMissingNum);
                }
            }

            // 斜め（左上から右下）
            {
                int missingCount = 0;
                String lastMissingNum = "";
                for (int i = 0; i < 5; i++) {
                    if (!hits[i][i]) {
                        missingCount++;
                        lastMissingNum = card.get(i).get(i);
                    }
                }
                if (missingCount == 0) bingoLines++;
                if (missingCount == 1) {
                    if (!waitNumsForThisPlayer.contains(lastMissingNum)) waitNumsForThisPlayer.add(lastMissingNum);
                }
            }

            // 斜め（右上から左下）👉【★ここを完璧に修正しました】
            {
                int missingCount = 0;
                String lastMissingNum = "";
                for (int i = 0; i < 5; i++) {
                    if (!hits[i][4 - i]) {
                        missingCount++;
                        lastMissingNum = card.get(i).get(4 - i);
                    }
                }
                if (missingCount == 0) bingoLines++;
                if (missingCount == 1) {
                    if (!waitNumsForThisPlayer.contains(lastMissingNum)) waitNumsForThisPlayer.add(lastMissingNum);
                }
            }

            if (bingoLines > 0) {
                currentBingo.add(new PlayerResult(pName, new Date(), nextNum));
            } else if (!waitNumsForThisPlayer.isEmpty()) {
                currentReach.add(new PlayerResult(pName, new Date(), nextNum));
                newWaitNumbers.put(pName, waitNumsForThisPlayer);
            }
        }

        for (PlayerResult newB : currentBingo) {
            boolean alreadyBingo = false;
            for (PlayerResult oldB : bingoPlayers) {
                if (oldB.getPlayerName().equals(newB.getPlayerName())) { alreadyBingo = true; break; }
            }
            if (!alreadyBingo) {
                bingoPlayers.add(0, newB);
                lastBingoTime = new Date();
            }
        }

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

    public synchronized void checkPlayerStatus(String name, List<List<String>> card) {
        if (name == null || card == null) return;

        boolean[][] hits = new boolean[5][5];
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                String numStr = card.get(r).get(c);
                int num = Integer.parseInt(numStr);
                if (num == 0 || drawnNumbers.contains(num)) {
                    hits[r][c] = true;
                }
            }
        }

        int bingoLines = 0;
        List<String> waitNums = new ArrayList<>();

        for (int r = 0; r < 5; r++) {
            int miss = 0; String lastNum = "";
            for (int c = 0; c < 5; c++) { if (!hits[r][c]) { miss++; lastNum = card.get(r).get(c); } }
            if (miss == 0) bingoLines++;
            if (miss == 1 && !waitNums.contains(lastNum)) waitNums.add(lastNum);
        }
        for (int c = 0; c < 5; c++) {
            int miss = 0; String lastNum = "";
            for (int r = 0; r < 5; r++) { if (!hits[r][c]) { miss++; lastNum = card.get(r).get(c); } }
            if (miss == 0) bingoLines++;
            if (miss == 1 && !waitNums.contains(lastNum)) waitNums.add(lastNum);
        }
        {
            int miss = 0; String lastNum = "";
            for (int i = 0; i < 5; i++) { if (!hits[i][i]) { miss++; lastNum = card.get(i).get(i); } }
            if (miss == 0) bingoLines++;
            if (miss == 1 && !waitNums.contains(lastNum)) waitNums.add(lastNum);
        }
        {
            int miss = 0; String lastNum = "";
            for (int i = 0; i < 5; i++) { if (!hits[i][4-i]) { miss++; lastNum = card.get(i).get(4-i); } }
            if (miss == 0) bingoLines++;
            if (miss == 1 && !waitNums.contains(lastNum)) waitNums.add(lastNum);
        }

        if (bingoLines > 0) {
            boolean already = false;
            for (PlayerResult b : bingoPlayers) { if (b.getPlayerName().equals(name)) { already = true; break; } }
            if (!already) {
                int lastNum = drawnNumbers.isEmpty() ? 0 : drawnNumbers.get(drawnNumbers.size() - 1);
                bingoPlayers.add(0, new PlayerResult(name, new Date(), lastNum));
                lastBingoTime = new Date();
            }
            reachPlayers.removeIf(p -> p.getPlayerName().equals(name));
            playerWaitNumbers.remove(name);
        } else if (!waitNums.isEmpty()) {
            playerWaitNumbers.put(name, waitNums);
            boolean already = false;
            for (PlayerResult r : reachPlayers) { if (r.getPlayerName().equals(name)) { already = true; break; } }
            boolean inBingo = false;
            for (PlayerResult b : bingoPlayers) { if (b.getPlayerName().equals(name)) { inBingo = true; break; } }
            
            if (!already && !inBingo) {
                int lastNum = drawnNumbers.isEmpty() ? 0 : drawnNumbers.get(drawnNumbers.size() - 1);
                reachPlayers.add(new PlayerResult(name, new Date(), lastNum));
            }
        } else {
            reachPlayers.removeIf(p -> p.getPlayerName().equals(name));
            playerWaitNumbers.remove(name);
        }
    }

    public synchronized void resetGame() {
        drawnNumbers.clear();
        bingoPlayers.clear();
        reachPlayers.clear();
        playerWaitNumbers.clear();
        playerCards.clear();
        allPlayers.clear();
        anonymousCount = 0;
        lastBingoTime = new Date();
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
