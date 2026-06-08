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

    public synchronized String generateAnonymousName() {
        this.anonymousCount++;
        return "ゲスト" + this.anonymousCount;
    }

    public void clearGameDataOnly() {
        this.drawnNumbers.clear();       
        this.bingoPlayers.clear();       
        this.reachPlayers.clear();       
        this.allPlayers.clear();         
        this.playerCards.clear();        
        this.playerWaitNumbers.clear();  
        this.lastBingoTime = new Date(); 
        this.anonymousCount = 0;
    }

    public int drawNumber() {
        List<Integer> pool = new ArrayList<>();
        for (int i = 1; i <= 75; i++) {
            if (!drawnNumbers.contains(i)) {
                pool.add(i);
            }
        }
        if (pool.isEmpty()) return -1;
        
        java.util.Collections.shuffle(pool);
        int nextNum = pool.get(0);
        drawnNumbers.add(nextNum);
        this.lastBingoTime = new Date(); 
        
        updateAllPlayersStatus();
        return nextNum;
    }

    // 👥 プレイヤー全員のビンゴ・リーチ状態を正しく更新する（完全ロック版）
    public void updateAllPlayersStatus() {
        // リーチはその都度変動するので毎回クリア
        reachPlayers.clear();
        
        int currentDrawnNumber = drawnNumbers.isEmpty() ? -1 : drawnNumbers.get(drawnNumbers.size() - 1);

        for (String name : playerCards.keySet()) {
            // 🚨【超重要】すでにビンゴ達成者一覧に名前がある人は、データ保護のため
            // これ以降の判定（ビンゴ再登録やリーチ計算）を完全にスキップして、絶対に上書きさせない！
            boolean alreadyBingo = false;
            for (PlayerResult p : bingoPlayers) {
                if (p.getPlayerName().equals(name)) {
                    alreadyBingo = true;
                    break;
                }
            }
            if (alreadyBingo) {
                continue; // 👈 このプレイヤーの処理は完全にスルーして次の人へ
            }

            List<List<String>> card = playerCards.get(name);
            List<String> waits = calculateActualWaitNumbers(card);
            playerWaitNumbers.put(name, waits);

            // 1. 新しく1列揃った人を検知した場合
            if (checkBingo(card)) {
                addBingoPlayer(name, currentDrawnNumber);
            } 
            // 2. まだビンゴしていないが、リーチ状態の場合
            else if (checkActualReachLines(card)) {
                addReachPlayer(name);
            }
        }
    }

    private boolean checkBingo(List<List<String>> card) {
        for (int i = 0; i < 5; i++) {
            if (countHitInLine(card.get(i)) == 5) return true;
        }
        for (int c = 0; c < 5; c++) {
            List<String> col = new ArrayList<>();
            for (int r = 0; r < 5; r++) { col.add(card.get(r).get(c)); }
            if (countHitInLine(col) == 5) return true;
        }
        List<String> d1 = new ArrayList<>();
        List<String> d2 = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            d1.add(card.get(i).get(i));
            d2.add(card.get(i).get(4 - i));
        }
        if (countHitInLine(d1) == 5) return true;
        if (countHitInLine(d2) == 5) return true;
        
        return false;
    }

    private boolean checkActualReachLines(List<List<String>> card) {
        for (int i = 0; i < 5; i++) {
            if (countHitInLine(card.get(i)) == 4) return true;
        }
        for (int c = 0; c < 5; c++) {
            List<String> col = new ArrayList<>();
            for (int r = 0; r < 5; r++) { col.add(card.get(r).get(c)); }
            if (countHitInLine(col) == 4) return true;
        }
        List<String> d1 = new ArrayList<>();
        List<String> d2 = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            d1.add(card.get(i).get(i));
            d2.add(card.get(i).get(4 - i));
        }
        if (countHitInLine(d1) == 4) return true;
        if (countHitInLine(d2) == 4) return true;
        
        return false;
    }

    private int countHitInLine(List<String> line) {
        int hit = 0;
        for (String cell : line) {
            if ("0".equals(cell) || drawnNumbers.contains(Integer.parseInt(cell))) {
                hit++;
            }
        }
        return hit;
    }

    private List<String> calculateActualWaitNumbers(List<List<String>> card) {
        List<String> waits = new ArrayList<>();
        for (int i = 0; i < 5; i++) { getWaitFromLine(card.get(i), waits); }
        for (int c = 0; c < 5; c++) {
            List<String> col = new ArrayList<>();
            for (int r = 0; r < 5; r++) { col.add(card.get(r).get(c)); }
            getWaitFromLine(col, waits);
        }
        List<String> d1 = new ArrayList<>();
        List<String> d2 = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            d1.add(card.get(i).get(i));
            d2.add(card.get(i).get(4 - i));
        }
        getWaitFromLine(d1, waits);
        getWaitFromLine(d2, waits);

        return waits;
    }

    private void getWaitFromLine(List<String> line, List<String> waits) {
        if (countHitInLine(line) == 4) {
            for (String cell : line) {
                if (!"0".equals(cell) && !drawnNumbers.contains(Integer.parseInt(cell))) {
                    if (!waits.contains(cell)) {
                        waits.add(cell);
                    }
                }
            }
        }
    }

    private void addBingoPlayer(String name, int currentDrawnNumber) {
        // 安全策として、ここでも既存プレイヤーの重複を絶対にブロック
        for (PlayerResult p : bingoPlayers) {
            if (p.getPlayerName().equals(name)) return;
        }
        Date now = new Date();
        bingoPlayers.add(0, new PlayerResult(name, now, currentDrawnNumber));
        this.lastBingoTime = now; 
        removeReachPlayer(name);
    }

    private void addReachPlayer(String name) {
        for (PlayerResult p : reachPlayers) {
            if (p.getPlayerName().equals(name)) return;
        }
        for (PlayerResult p : bingoPlayers) {
            if (p.getPlayerName().equals(name)) return;
        }
        reachPlayers.add(0, new PlayerResult(name, new Date(), 0));
    }

    public void removeReachPlayer(String name) {
        reachPlayers.removeIf(p -> p.getPlayerName().equals(name));
    }

    public List<String> getWaitNumbers(String name) {
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

    public void setPlayerCard(String playerName, List<List<String>> card) {
        playerCards.put(playerName, card);
        updateAllPlayersStatus(); 
    }
}
