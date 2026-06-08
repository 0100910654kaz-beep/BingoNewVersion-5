<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="servlet.BingoGame" %>
<%@ page import="servlet.PlayerResult" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%
    BingoGame game = (BingoGame) request.getAttribute("game");
    String error = (String) request.getAttribute("error");
    String gameId = (game != null) ? game.getGameId() : "";
    
    String playerName = (String) request.getAttribute("confirmedPlayerName");
    if (playerName == null) {
        playerName = (String) session.getAttribute("myConfirmedName");
    }
    if (playerName == null) { playerName = ""; }

    // 🔄【重要】司会者がリセット（数字が0個）したら、古いカードを強制消去
    if (game != null && game.getDrawnNumbers().isEmpty()) {
        session.removeAttribute("card");
    }

    List<List<String>> bingoCard = (List<List<String>>) session.getAttribute("card");

    // 🎲 カードが消えていたら（リセット直後など）、今の名前のまま自動で新しいカードをランダム生成
    if (bingoCard == null && game != null && !playerName.isEmpty()) {
        List<List<Integer>> columns = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            List<Integer> pool = new ArrayList<>();
            for (int j = 1; j <= 15; j++) { pool.add(i * 15 + j); }
            Collections.shuffle(pool);
            columns.add(pool.subList(0, 5));
        }
        bingoCard = new ArrayList<>();
        for (int r = 0; r < 5; r++) {
            List<String> row = new ArrayList<>();
            for (int c = 0; c < 5; c++) {
                if (r == 2 && c == 2) { row.add("0"); }
                else { row.add(String.valueOf(columns.get(c).get(r))); }
            }
            bingoCard.add(row);
        }
        session.setAttribute("card", bingoCard);
        game.setPlayerCard(playerName, bingoCard); // ⚡ サーバー側にも即座に新カードを同期して登録
        game.checkAllPlayersStatus();              // ⚡ リーチ判定を即座に再計算
    }

    List<Integer> reverseDrawnNumbers = new ArrayList<>();
    int ballCount = 0;
    if (game != null) {
        reverseDrawnNumbers.addAll(game.getDrawnNumbers());
        ballCount = reverseDrawnNumbers.size();
        Collections.reverse(reverseDrawnNumbers);
    }

    boolean myBingo = false;
    if (game != null && !playerName.isEmpty()) {
        for (PlayerResult p : game.getBingoPlayers()) {
            if (p.getPlayerName().equals(playerName)) {
                myBingo = true;
                break;
            }
        }
    }
    
    boolean myReach = false;
    if (game != null && !playerName.isEmpty()) {
        for (PlayerResult p : game.getReachPlayers()) {
            if (p.getPlayerName().equals(playerName)) {
                myReach = true;
                break;
            }
        }
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ビンゴ大会 - プレイヤー画面</title>
    <style>
        body { font-family: Arial, sans-serif; background-color: #f7f9fa; padding: 10px; text-align: center; margin: 0; }
        .container { max-width: 450px; margin: 0 auto; background: white; padding: 15px; border-radius: 12px; box-shadow: 0 4px 10px rgba(0,0,0,0.08); }
        h2 { color: #333; margin-top: 5px; margin-bottom: 10px; font-size: 20px; }
        .info-box { background: #eef2f3; padding: 8px; border-radius: 6px; margin-bottom: 12px; font-size: 14px; font-weight: bold; }
        .bingo-table { width: 100%; margin: 10px 0; border-collapse: separate; border-spacing: 6px; }
        .bingo-cell { width: 18%; aspect-ratio: 1; border: 2px solid #ccc; font-size: 18px; font-weight: bold; text-align: center; vertical-align: middle; background: #fff; border-radius: 8px; color: #333; }
        .bingo-cell.hit { background: #ffadad; border-color: #ff6b6b; color: #d00000; position: relative; }
        .bingo-cell.hit::after { content: "✓"; position: absolute; top: 2px; right: 4px; font-size: 10px; color: #ff6b6b; }
        .free-cell { background: #ffd166 !important; border-color: #f5a623 !important; color: #d00000 !important; }
        .list-box { margin-top: 15px; text-align: left; background: #fff; padding: 10px; border-radius: 8px; border: 1px solid #ddd; }
        .history-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 5px; margin-top: 8px; }
        .history-cell { padding: 6px 0; background: #eef2f3; border-radius: 4px; font-size: 12px; font-weight: bold; text-align: center; color: #555; }
        .history-cell.newest { animation: pulse 1s infinite alternate; font-size: 14px; }
        @keyframes pulse { from { transform: scale(1); } to { transform: scale(1.1); } }
        
        /* 🎉 ビンゴ・リーチお祝いアニメーション */
        .overlay { position: fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.75); color:white; display:flex; flex-direction:column; justify-content:center; align-items:center; z-index:9999; }
        .firework { font-size: 80px; animation: bounce 0.6s infinite alternate; }
        @keyframes bounce { from { transform: scale(1); } to { transform: scale(1.2); } }
    </style>
    <% if (game != null && !myBingo) { %>
        <script>
            setInterval(function() {
                window.location.href = "BingoServlet?userType=player";
            }, 5000);
        </script>
    <% } %>
</head>
<body>

<% if (myBingo) { %>
    <div class="overlay" onclick="window.location.href='BingoServlet?userType=player'">
        <div class="firework">🎆🏆</div>
        <h1 style="font-size:36px; color:#ffd166; margin:10px 0;">BINGO !!</h1>
        <p style="font-size:18px; font-weight:bold;"><%= playerName %> さん、おめでとうございます！</p>
        <p style="font-size:13px; color:#ccc; margin-top:20px;">画面をタップするとカードに戻ります (5秒ごとに自動更新中)</p>
    </div>
    <script>
        setInterval(function() {
            window.location.href = "BingoServlet?userType=player";
        }, 5000);
    </script>
<% } %>

<div class="container">
    <h2>🎯 ビンゴ大会 🎯</h2>

    <% if (game == null || gameId.isEmpty()) { %>
        <div class="info-box" style="background:#ffe3e3; color:#c0392b;">
            <%= (error != null) ? error : "⏳ 司会者がゲームを開始するのを待っています..." %>
        </div>
        <form action="BingoServlet" method="get" style="margin-top:20px;">
            <input type="hidden" name="action" value="join">
            <div style="margin-bottom:12px;">
                <label style="font-weight:bold; display:block; margin-bottom:5px;">部屋番号 (4桁):</label>
                <input type="text" name="gameId" style="padding:8px; width:60%; border-radius:4px; border:1px solid #ccc; font-size:16px; text-align:center;" required>
            </div>
            <div style="margin-bottom:20px;">
                <label style="font-weight:bold; display:block; margin-bottom:5px;">あなたの名前 (空欄でゲスト):</label>
                <input type="text" name="playerName" style="padding:8px; width:60%; border-radius:4px; border:1px solid #ccc; font-size:16px; text-align:center;" placeholder="例: たかし">
            </div>
            <button type="submit" style="padding:10px 25px; background:#2ec4b6; color:white; border:none; border-radius:6px; font-size:16px; font-weight:bold; cursor:pointer;">参加する</button>
        </form>
    <% } else { %>
        <div class="info-box">
            部屋番号: <span style="color:#e71d36;"><%= gameId %></span> &nbsp;|&nbsp; 
            名前: <span style="color:#011627;"><%= playerName %> さん</span>
            <% if (myReach && !myBingo) { %>
                <div style="color:#ff9f1c; margin-top:4px; font-size:15px; animation: pulse 0.5s infinite alternate;">🔥 REACH (リーチ中!) 🔥</div>
            <% } %>
        </div>

        <div style="font-size:13px; color:#666; margin-bottom:5px; text-align:right;">
            現在の玉数: <%= ballCount %> / 75 球
        </div>

        <% if (bingoCard != null) { %>
            <table class="bingo-table">
                <% for (int r = 0; r < 5; r++) { %>
                    <tr>
                        <% for (int c = 0; c < 5; c++) { 
                            String num = bingoCard.get(r).get(c);
                            boolean isHit = game.getDrawnNumbers().contains(Integer.parseInt(num)) || num.equals("0");
                            if (num.equals("0")) { %>
                                <td class="bingo-cell free-cell hit">FREE</td>
                            <% } else { %>
                                <td class="bingo-cell <%= isHit ? "hit" : "" %>"><%= num %></td>
                            <% } 
                        } %>
                    </tr>
                <% } %>
            </table>
        <% } %>

        <div class="list-box">
            <h3 style="margin: 5px 0; font-size:14px;">📊 出た数字一覧（最新が赤）</h3>
            <div class="history-grid">
                <% for (int i = 0; i < reverseDrawnNumbers.size(); i++) { 
                    int num = reverseDrawnNumbers.get(i);
                    if (i == 0) { %>
                        <div class="history-cell newest" style="background:#ff6b6b; color:white;"><%= num %></div>
                    <% } else { %>
                        <div class="history-cell"><%= num %></div>
                    <% }
                } %>
            </div>
        </div>
    <% } %>
</div>
</body>
</html>
