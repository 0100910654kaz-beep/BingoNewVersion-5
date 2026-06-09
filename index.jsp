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

    // ⚡ 司会者がリセット（数字が0個）したら、カードと名前の記憶を完全に全消去してトップへ戻す！
    if (game != null && game.getDrawnNumbers().isEmpty()) {
        session.removeAttribute("card");
        session.removeAttribute("myConfirmedName");
        playerName = ""; 
    }

    List<List<String>> bingoCard = (List<List<String>>) session.getAttribute("card");

    if (bingoCard == null && game != null && !playerName.isEmpty()) {
        List<List<Integer>> columns = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            List<Integer> pool = new ArrayList<>();
            for (int j = (i * 15) + 1; j <= (i * 15) + 15; j++) {
                pool.add(j);
            }
            Collections.shuffle(pool);
            columns.add(pool.subList(0, 5));
        }

        bingoCard = new ArrayList<>();
        for (int r = 0; r < 5; r++) {
            List<String> row = new ArrayList<>();
            for (int c = 0; c < 5; c++) {
                if (r == 2 && c == 2) {
                    row.add("0");
                } else {
                    row.add(String.valueOf(columns.get(c).get(r)));
                }
            }
            bingoCard.add(row);
        }
        session.setAttribute("card", bingoCard);
    }

    List<Integer> reverseDrawnNumbers = new ArrayList<>();
    int ballCount = 0;
    if (game != null) {
        reverseDrawnNumbers.addAll(game.getDrawnNumbers());
        ballCount = reverseDrawnNumbers.size();
        Collections.reverse(reverseDrawnNumbers);
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ビンゴ大会 - プレイヤー画面</title>
    <style>
        body { font-family: Arial, sans-serif; background-color: #f4f7f6; padding: 10px; text-align: center; margin: 0; }
        .container { max-width: 450px; margin: 0 auto; background: white; padding: 15px; border-radius: 12px; box-shadow: 0 4px 10px rgba(0,0,0,0.1); box-sizing: border-box; }
        h1 { color: #2c3e50; font-size: 20px; margin-top: 5px; margin-bottom: 15px; }
        .error-msg { color: #e63946; background: #ffe3e5; padding: 10px; border-radius: 6px; margin-bottom: 15px; font-size: 14px; font-weight: bold; }
        .form-group { margin-bottom: 15px; text-align: left; }
        label { font-weight: bold; color: #34495e; font-size: 14px; display: block; margin-bottom: 5px; }
        input[type="text"] { width: 100%; padding: 10px; border: 1px solid #ccc; border-radius: 6px; font-size: 16px; box-sizing: border-box; }
        .btn { display: block; width: 100%; background: #2a9d8f; color: white; padding: 12px; border: none; border-radius: 6px; font-size: 16px; font-weight: bold; cursor: pointer; text-decoration: none; box-sizing: border-box; text-align: center; }
        .btn:hover { background: #21867a; }
        .status-panel { background: #eef2f3; padding: 10px; border-radius: 8px; margin-bottom: 15px; font-size: 14px; font-weight: bold; color: #2c3e50; }
        .bingo-table { width: 100%; border-collapse: separate; border-spacing: 6px; margin: 15px 0; table-layout: fixed; }
        .bingo-cell { background: #fff; border: 2px solid #bdc3c7; border-radius: 8px; font-size: 18px; font-weight: bold; height: 55px; text-align: center; color: #2c3e50; box-shadow: inset 0 -3px 0 #bdc3c7; transition: all 0.2s ease; word-wrap: break-word; }
        .bingo-cell.hit { background: #e63946; color: white; border-color: #b11e29; box-shadow: inset 0 -3px 0 #91141e; }
        .bingo-cell.free-cell { background: #f4a261; color: white; border-color: #e76f51; box-shadow: inset 0 -3px 0 #d95d39; font-size: 12px; }
        .list-box { background: #f8f9fa; border: 1px solid #e9ecef; border-radius: 8px; padding: 10px; margin-top: 15px; }
        .history-grid { display: flex; flex-wrap: wrap; gap: 6px; justify-content: center; max-height: 120px; overflow-y: auto; padding: 5px; }
        .history-cell { background: #e9ecef; color: #495057; font-weight: bold; padding: 6px 10px; border-radius: 20px; font-size: 13px; min-width: 24px; text-align: center; }
        .history-cell.newest { animation: pulse 1s infinite alternate; font-weight: 900; }
        @keyframes pulse { from { transform: scale(1); } to { transform: scale(1.15); } }
    </style>
</head>
<body>
<div class="container">
    <% if (error != null) { %>
        <div class="error-msg"><%= error %></div>
    <% } %>

    <% if (game == null || playerName.isEmpty() || bingoCard == null) { %>
        <h1>🎉 ビンゴ大会に参戦</h1>
        <form action="BingoServlet" method="post">
            <input type="hidden" name="action" value="join">
            <div class="form-group">
                <label for="gameId">🔑 部屋ID (4桁)</label>
                <input type="text" id="gameId" name="gameId" value="<%= gameId %>" placeholder="例: 1234" maxlength="4" required autocomplete="off">
            </div>
            <div class="form-group">
                <label for="playerName">👤 あなたのお名前</label>
                <input type="text" id="playerName" name="playerName" placeholder="例: たろう" maxlength="10" required autocomplete="off">
            </div>
            <button type="submit" class="btn">ゲームに参加する</button>
        </form>
    <% } else { %>
        <h1>🎰 ビンゴカード</h1>
        <div class="status-panel">
            部屋: <span style="color:#e63946;"><%= gameId %></span> &nbsp;|&nbsp; 
            名前: <span style="color:#2a9d8f;"><%= playerName %></span> &nbsp;|&nbsp; 
            抽出: <%= ballCount %> / 75 球
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
