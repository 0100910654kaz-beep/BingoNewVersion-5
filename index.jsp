<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"% warm%>
<%@ page import="servlet.BingoGame" %>
<%@ page import="servlet.PlayerResult" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%
    BingoGame game = (BingoGame) request.getAttribute("game");
    String error = (String) request.getAttribute("error");
    String gameId = (game != null) ? game.getGameId() : "";
    
    // 🚨【連動リセット検知】司会者がリセットしてサーバー側が空になった場合、セッションの記憶を完全に抹殺
    if (game != null && game.getDrawnNumbers().isEmpty() && game.getPlayerCount() == 0) {
        session.removeAttribute("card");
        session.removeAttribute("myConfirmedName");
        session.removeAttribute("myCurrentGameId");
    }

    String playerName = (String) request.getAttribute("confirmedPlayerName");
    if (playerName == null) {
        playerName = (String) session.getAttribute("myConfirmedName");
    }
    if (playerName == null) { playerName = ""; }

    // 司会者がリセット（数字が0個）した際のカード個別破棄
    if (game != null && game.getDrawnNumbers().isEmpty()) {
        session.removeAttribute("card");
    }

    List<List<String>> bingoCard = (List<List<String>>) session.getAttribute("card");

    // 🎲 カードが破棄されて空っぽになったら、名前を維持したまま、新しいランダムカードを全自動で再生成
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

        List<List<String>> newCard = new ArrayList<>();
        for (int r = 0; r < 5; r++) {
            List<String> row = new ArrayList<>();
            for (int c = 0; c < 5; c++) {
                if (r == 2 && c == 2) {
                    row.add("0"); // 中央はFREE(0)
                } else {
                    row.add(String.valueOf(columns.get(c).get(r)));
                }
            }
            newCard.add(row);
        }
        bingoCard = newCard;
        session.setAttribute("card", bingoCard);
        game.setPlayerCard(playerName, bingoCard);
        
        // 念のためカードを再生成した後に最新ステータスをチェック
        game.checkPlayerStatus(playerName, bingoCard);
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
    <title>デジタルビンゴ大会</title>
    <style>
        body { font-family: 'Helvetica Neue', Arial, sans-serif; background-color: #f0f4f8; margin: 0; padding: 10px; color: #333; display: flex; justify-content: center; }
        .game-container { width: 100%; max-width: 450px; background: white; padding: 20px; border-radius: 16px; box-shadow: 0 4px 20px rgba(0,0,0,0.08); box-sizing: border-box; }
        h1 { font-size: 24px; color: #1e3a8a; text-align: center; margin-top: 5px; margin-bottom: 15px; }
        .error-msg { background-color: #ffe3e3; color: #d63031; padding: 12px; border-radius: 8px; margin-bottom: 15px; font-size: 14px; text-align: center; font-weight: bold; border-left: 5px solid #d63031; }
        .login-box { background: #f8fafc; border: 1px solid #e2e8f0; padding: 20px; border-radius: 12px; margin-top: 10px; }
        .login-box h2 { font-size: 18px; margin-top: 0; color: #334155; text-align: center; }
        .form-group { margin-bottom: 15px; text-align: left; }
        .form-group label { display: block; font-size: 14px; font-weight: bold; margin-bottom: 5px; color: #475569; }
        .form-group input { width: 100%; padding: 12px; border: 1px solid #cbd5e1; border-radius: 8px; font-size: 16px; box-sizing: border-box; }
        .btn-submit { width: 100%; background: #2563eb; color: white; border: none; padding: 14px; border-radius: 8px; font-size: 16px; font-weight: bold; cursor: pointer; transition: background 0.2s; }
        .btn-submit:hover { background: #1d4ed8; }
        .status-card { background: linear-gradient(135deg, #1e3a8a, #3b82f6); color: white; padding: 15px; border-radius: 12px; text-align: center; margin-bottom: 15px; box-shadow: 0 4px 10px rgba(37,99,235,0.2); }
        .status-name { font-size: 18px; font-weight: bold; }
        .status-room { font-size: 12px; opacity: 0.9; margin-top: 4px; }
        .ball-counter { font-size: 14px; background: #f1f5f9; padding: 8px; border-radius: 20px; display: inline-block; font-weight: bold; color: #475569; margin-bottom: 15px; }
        .bingo-table { width: 100%; border-collapse: separate; border-spacing: 6px; margin: 0 auto 15px auto; }
        .bingo-cell { width: 18%; aspect-ratio: 1; text-align: center; font-size: 20px; font-weight: bold; background: #f8fafc; border: 2px solid #e2e8f0; border-radius: 10px; color: #334155; transition: all 0.2s; }
        .bingo-cell.hit { background: #ef4444; border-color: #dc2626; color: white; box-shadow: inset 0 0 10px rgba(0,0,0,0.2); animation: pop 0.3s ease-in-out; }
        .bingo-cell.free-cell { font-size: 12px; background: #e2e8f0; color: #64748b; }
        .bingo-cell.free-cell.hit { background: #f59e0b; border-color: #d97706; color: white; }
        .list-box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 12px; margin-top: 15px; text-align: left; }
        .history-grid { display: flex; flex-wrap: wrap; gap: 6px; max-height: 90px; overflow-y: auto; padding: 4px; }
        .history-cell { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; background: #e2e8f0; border-radius: 50%; font-size: 13px; font-weight: bold; color: #475569; }
        .history-cell.newest { transform: scale(1.1); box-shadow: 0 2px 5px rgba(0,0,0,0.2); font-size: 15px; }
        @keyframes pop { 0% { transform: scale(1); } 50% { transform: scale(1.15); } 100% { transform: scale(1); } }
    </style>
</head>
<body>

<div class="game-container">
    <h1>🎉 デジタルビンゴ大会</h1>

    <% if (error != null) { %>
        <div class="error-msg"><%= error %></div>
    <% } %>

    <%-- 🚨【画面分岐の条件】名前がない、またはセッションがリセットされていたら確実に登録画面（ID入力画面）を出す --%>
    <% if (playerName.isEmpty() || game == null) { %>
        <div class="login-box">
            <h2>ビンゴルームに入室</h2>
            <form action="BingoServlet" method="post">
                <input type="hidden" name="action" value="join">
                <div class="form-group">
                    <label for="gameId">① 4桁の部屋番号</label>
                    <input type="text" id="gameId" name="gameId" value="<%= gameId %>" placeholder="例: 1234" inputmode="numeric" pattern="[0-9]{4}" required>
                </div>
                <div class="form-group">
                    <label for="playerName">② あなたのお名前</label>
                    <input type="text" id="playerName" name="playerName" placeholder="例: 佐藤" required>
                </div>
                <button type="submit" class="btn-submit">カードを発行して部屋に入る</button>
            </form>
        </div>
    <% } else { %>
        <%-- 🎮 プレイヤー用ゲーム画面 --%>
        <div class="status-card">
            <div class="status-name">👤 <%= playerName %> さんのカード</div>
            <div class="status-room">部屋番号: <%= gameId %></div>
        </div>

        <div style="text-align: center;">
            <div class="ball-counter">
                現在の抽選状況: <%= ballCount %> / 75 球
            </div>
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
                                <td class="bingo-cell <%= isHit ? \"hit\" : \"\" %>"><%= num %></td>
                            <% } 
                        } %>
                    </tr>
                <% } %>
            </table>
        <% } %>

        <div class="list-box">
            <h3 style="margin: 5px 0; font-size:14px; color:#475569;">📊 出た数字一覧（最新が赤）</h3>
            <div class="history-grid">
                <% for (int i = 0; i < reverseDrawnNumbers.size(); i++) { \n                    int num = reverseDrawnNumbers.get(i);\n                    if (i == 0) { %>
                        <div class="history-cell newest" style="background:#ff6b6b; color:white;"><%= num %></div>
                    <% } else { %>
                        <div class="history-cell"><%= num %></div>
                    <% }\n                } %>
            </div>
        </div>
    <% } %>
</div>

</body>
</html>
