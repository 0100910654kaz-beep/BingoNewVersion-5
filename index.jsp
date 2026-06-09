<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%><%@ page import="servlet.BingoGame" %><%@ page import="servlet.PlayerResult" %><%@ page import="java.util.List" %><%@ page import="java.util.ArrayList" %><%@ page import="java.util.Collections" %><%
    BingoGame game = (BingoGame) request.getAttribute("game");
    String error = (String) request.getAttribute("error");
    String gameId = (game != null) ? game.getGameId() : "";
    
    String playerName = (String) request.getAttribute("confirmedPlayerName");
    if (playerName == null) {
        playerName = (String) session.getAttribute("myConfirmedName");
    }
    if (playerName == null) { playerName = ""; }

    // ⚡【重要・カード切り替えの鍵】司会者がリセット（数字が0個）したら、古いカードを即座に破棄！
    if (game != null && game.getDrawnNumbers().isEmpty()) {
        session.removeAttribute("card");
    }

    List<List<String>> bingoCard = (List<List<String>>) session.getAttribute("card");

    // 🎲 カードが破棄されて空っぽになったら、名前を維持したまま、新しいランダムカードを全自動で再生成！
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
        for (int row = 0; row < 5; row++) {
            List<String> rowList = new ArrayList<>();
            for (int col = 0; col < 5; col++) {
                if (row == 2 && col == 2) {
                    rowList.add("0"); // 真ん中はFREE
                } else {
                    rowList.add(String.valueOf(columns.get(col).get(row)));
                }
            }
            bingoCard.add(rowList);
        }
        // 生成したカードをセッションとゲーム内共通メモリに同時に焼き付ける（完全同期）
        session.setAttribute("card", bingoCard);
        game.setPlayerCard(playerName, bingoCard);
    }

    // 最新の当選状況に合わせてバックグラウンドでリーチ・ビンゴを自動チェック
    if (game != null && bingoCard != null && !playerName.isEmpty()) {
        game.checkPlayerStatus(playerName, bingoCard);
    }

    List<Integer> reverseDrawnNumbers = new ArrayList<>();
    int ballCount = 0;
    if (game != null) {
        reverseDrawnNumbers.addAll(game.getDrawnNumbers());
        ballCount = reverseDrawnNumbers.size();
        Collections.reverse(reverseDrawnNumbers);
    }
%><!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ビンゴ大会 - プレイヤー画面</title>
    <style>
        body { font-family: 'Helvetica Neue', Arial, sans-serif; background-color: #f4f7f6; color: #333; margin: 0; padding: 10px; text-align: center; }
        .container { max-width: 450px; margin: 0 auto; background: white; padding: 15px; border-radius: 12px; box-shadow: 0 4px 10px rgba(0,0,0,0.08); box-sizing: border-box; }
        h1 { font-size: 24px; color: #2b3a42; margin-top: 5px; margin-bottom: 15px; }
        .login-container { padding: 10px 5px; }
        .input-group { margin-bottom: 15px; text-align: left; }
        .input-group label { display: block; font-weight: bold; margin-bottom: 5px; font-size: 14px; }
        .input-group input { width: 100%; padding: 10px; border: 1px solid #ccc; border-radius: 6px; box-sizing: border-box; font-size: 16px; }
        .btn-join { width: 100%; padding: 12px; background: #007bff; color: white; border: none; border-radius: 6px; font-size: 16px; font-weight: bold; cursor: pointer; }
        .error-msg { color: #e63946; background: #ffe3e3; padding: 10px; border-radius: 6px; margin-bottom: 15px; font-weight: bold; font-size: 14px; }
        .player-info { margin-bottom: 15px; padding: 12px; background: #eef2f3; border-radius: 8px; box-sizing: border-box; }
        .bingo-table { width: 100%; margin: 15px 0; border-collapse: separate; border-spacing: 6px; table-layout: fixed; }
        .bingo-cell { height: 60px; text-align: center; vertical-align: middle; background: #f9f9f9; border: 2px solid #ddd; font-size: 18px; font-weight: bold; border-radius: 8px; color: #444; }
        .bingo-cell.hit { background: #ff6b6b !important; color: white !important; border-color: #ee5253 !important; text-shadow: 0 1px 3px rgba(0,0,0,0.2); }
        .bingo-cell.free-cell { background: #ffeaa7; color: #d63031; font-size: 14px; }
        .status-banner { padding: 10px; border-radius: 8px; font-size: 20px; font-weight: bold; margin-bottom: 15px; }
        .status-playing { background: #e3f2fd; color: #0d47a1; }
        .status-reach { background: #fff3e0; color: #e65100; animation: blink 1s infinite alternate; }
        .status-bingo { background: #e8f5e9; color: #1b5e20; font-size: 24px; animation: bounce 0.5s infinite alternate; }
        .list-box { background: #f8f9fa; border: 1px solid #e9ecef; border-radius: 8px; padding: 10px; margin-top: 15px; box-sizing: border-box; }
        .history-grid { display: flex; flex-wrap: wrap; gap: 6px; justify-content: center; max-height: 100px; overflow-y: auto; padding: 5px; }
        .history-cell { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; background: #e9ecef; border-radius: 50%; font-size: 14px; font-weight: bold; color: #495057; }
        @keyframes blink { 0% { opacity: 0.8; } 100% { opacity: 1; } }
        @keyframes bounce { 0% { transform: translateY(0); } 100% { transform: translateY(-4px); } }
    </style>
</head>
<body>
<div class="container">
    <h1>🎉 ビンゴ大会</h1>

    <%-- 🚪 部屋に参加していない（初期状態）時のログインフォーム --%>
    <% if (game == null || playerName.isEmpty()) { %>
        <div class="login-container">
            <h2 style="font-size: 18px; margin-bottom: 15px; color:#555;">ゲームに参加する</h2>
            
            <% if (error != null) { %>
                <div class="error-msg"><%= error %></div>
            <% } %>
            
            <form action="BingoServlet" method="POST">
                <input type="hidden" name="action" value="join">
                
                <div class="input-group">
                    <label for="gameId">🔑 4桁の部屋番号</label>
                    <input type="text" id="gameId" name="gameId" placeholder="例: 1234" maxlength="4" value="<%= gameId %>" required>
                </div>
                
                <div class="input-group">
                    <label for="playerName">👤 あなたのお名前</label>
                    <input type="text" id="playerName" name="playerName" placeholder="例: 佐藤" required>
                    
                    <%-- 💡 同姓同名トラブルを未然に防ぐための強力な注意書きを追加 --%>
                    <p style="color: #e63946; font-size: 12px; margin-top: 8px; margin-bottom: 0; line-height: 1.5; font-weight: bold; text-align: left;">
                        ⚠️ ※同姓同名の方がいる場合は、後ろに番号やニックネーム（例：さとう２）の様に番号をつけて唯一無二の名前に成るようにしてください
                    </p>
                </div>
                
                <button type="submit" class="btn-join">部屋に入る 🎲</button>
            </form>
        </div>
    <% } else { %>
        
        <%-- 🎮 ログイン成功後のメインビンゴ画面 --%>
        <div class="player-info">
            <p style="font-size: 16px; margin: 0; font-weight: bold;">
                部屋: <span style="color: #007bff;"><%= gameId %></span> ｜ 
                名前: <span style="color: #2b3a42;"><%= playerName %> さん</span>
            </p>
            <%-- 💡 システム側でナンバリング変換が行われたことをプレイヤーに優しく共有 --%>
            <p style="font-size: 11px; color: #666; margin: 5px 0 0 0; line-height: 1.3;">
                ※同じ名前の人が同じ部屋にいた場合、後ろに自動で番号がついている場合があります。
            </p>
        </div>

        <%-- 状態表示バナー（全自動リアルタイム反映） --%>
        <%
            boolean isBingo = false;
            for (PlayerResult p : game.getBingoPlayers()) {
                if (p.getPlayerName().equals(playerName)) { isBingo = true; break; }
            }
            boolean isReach = false;
            for (PlayerResult p : game.getReachPlayers()) {
                if (p.getPlayerName().equals(playerName)) { isReach = true; break; }
            }

            if (isBingo) {
        %>
            <div class="status-banner status-bingo">✨ 完 璧 💥 BINGO !! ✨</div>
        <% } else if (isReach) { %>
            <div class="status-banner status-reach">🔥 REACH!! あと <%= game.getWaitNumbers(playerName).size() %> マス 🔥</div>
        <% } else { %>
            <div class="status-banner status-playing">🎮 ビンゴを楽しもう！</div>
        <% } %>

        <div style="font-size: 14px; font-weight: bold; color:#666; margin-bottom: 5px;">
            現在の抽選球数: <%= ballCount %> / 75 球
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
                    <% }\n                } %>
            </div>
        </div>
    <% } %>
</div>
</body>
</html>
