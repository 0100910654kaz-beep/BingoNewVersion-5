<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="servlet.BingoGame" %>
<%@ page import="servlet.PlayerResult" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%
    BingoGame game = (BingoGame) request.getAttribute("game");
    String gameId = (game != null) ? game.getGameId() : "まだ開始していません";

    List<Integer> reverseDrawnNumbers = new ArrayList<>();
    int ballCount = 0;
    if (game != null) {
        reverseDrawnNumbers.addAll(game.getDrawnNumbers());
        ballCount = reverseDrawnNumbers.size();
        Collections.reverse(reverseDrawnNumbers);
    }

    String currentValidDays = request.getParameter("validDays");
    if (currentValidDays == null || currentValidDays.isEmpty()) {
        currentValidDays = "8"; // 最初の初期値
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>ビンゴ大会 - 司会者画面</title>
    <style>
        body { font-family: Arial, sans-serif; background-color: #eef2f3; padding: 20px; text-align: center; }
        .admin-container { max-width: 800px; margin: 0 auto; background: white; padding: 30px; border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.1); }
        h1 { color: #2b3a42; margin-bottom: 20px; }
        .info-panel { background: #f8f9fa; padding: 15px; border-radius: 8px; margin-bottom: 20px; font-size: 18px; border: 1px solid #dee2e6; }
        .grid-container { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; text-align: left; }
        .panel { background: #fff; padding: 20px; border-radius: 8px; border: 1px solid #dee2e6; }
        h3 { margin-top: 0; color: #495057; border-bottom: 2px solid #dee2e6; padding-bottom: 5px; }
        .btn { padding: 10px 20px; font-size: 16px; font-weight: bold; border: none; border-radius: 5px; cursor: pointer; margin: 5px; }
        .btn-draw { background-color: #2b8a3e; color: white; }
        .btn-reset { background-color: #e63946; color: white; }
        .number-display { font-size: 48px; font-weight: bold; color: #e63946; margin: 20px 0; min-height: 58px; }
        .history-list { display: flex; flex-wrap: wrap; gap: 5px; list-style: none; padding: 0; }
        .history-item { width: 35px; height: 35px; background: #e9ecef; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 14px; color: #495057; }
        .history-item.first { background: #ffc9c9; color: #c92a2a; border: 2px solid #c92a2a; }
        ul { padding-left: 20px; margin: 0; }
        li { margin-bottom: 8px; font-size: 15px; }
    </style>
    <script>
        // 5秒ごとに自動リロードして最新の参加状況やビンゴ者一覧を取得するタイマー
        setInterval(function() {
            // ⚡【超重要】画面内に「有効日数の入力欄(id="validDaysInput")」がある＝まだ部屋を作っていない初期状態
            // この時は、大山さんが日数をカチカチ変更している最中なので、5秒タイマーの自動リロードを【絶対に】動かさずストップします！
            var daysInput = document.getElementById("validDaysInput");
            if (daysInput) {
                return; // ここで処理を終了し、勝手なリロードを完全に阻止します
            }

            // 部屋が作成された後は、ここを通って5秒ごとに安全にリアルタイム更新されます
            window.location.href = "BingoServlet?userType=admin";
        }, 5000);
    </script>
</head>
<body>

<div class="admin-container">
    <h1>🎤 ビンゴ大会 司会者コントロール 🎤</h1>

    <div class="info-panel">
        部屋番号 (ゲームID): <span style="font-size: 24px; font-weight: bold; color: #e63946;"><%= gameId %></span>
        <span style="font-size: 14px; color: #6c757d; margin-left: 15px;">(現在の参加者数: <%= (game != null) ? game.getPlayerCount() : 0 %> 人)</span>
    </div>

    <% if (game == null || "まだ開始していません".equals(gameId)) { %>
        <div class="panel" style="text-align: center; margin-bottom: 20px; background: #fff5f5;">
            <p style="font-weight: bold; color: #c92a2a; margin-top: 0;">ビンゴゲームの部屋がまだ作成されていません。</p>
            <form action="BingoServlet" method="get">
                <input type="hidden" name="userType" value="admin">
                <input type="hidden" name="action" value="create">
                <label style="font-weight: bold;">部屋の有効日数: </label>
                <input type="number" id="validDaysInput" name="validDays" value="<%= currentValidDays %>" style="width:60px; padding:5px; text-align:center; font-size:16px;" min="1" required> 日間
                <br><br>
                <button type="submit" class="btn btn-draw" style="background:#228be6;">新規に部屋を作成する</button>
            </form>
        </div>
    <% } %>

    <% if (game != null && !"まだ開始していません".equals(gameId)) { %>
        <div style="margin-bottom: 25px;">
            <form action="BingoServlet" method="get" style="display:inline;">
                <input type="hidden" name="userType" value="admin">
                <input type="hidden" name="action" value="draw">
                <button type="submit" class="btn btn-draw">🔮 玉を1個引く</button>
            </form>

            <form action="BingoServlet" method="get" style="display:inline;" onsubmit="return confirm('本当にゲームをリセットしますか？出た数字や全員のカードが初期化されます。');">
                <input type="hidden" name="userType" value="admin">
                <input type="hidden" name="action" value="reset">
                <button type="submit" class="btn btn-reset">🔄 ゲームをリセット</button>
            </form>
        </div>

        <div class="panel" style="margin-bottom: 20px;">
            <h3 style="border-bottom:1px solid #dee2e6;">📢 当選番号のコール</h3>
            <div class="number-display">
                <%= (game.getDrawnNumbers().isEmpty()) ? "⏳ スタートを待っています" : game.getDrawnNumbers().get(game.getDrawnNumbers().size() - 1) + " 番" %>
            </div>
            <div style="font-size: 14px; color: #6c757d; text-align: right; margin-bottom: 5px;">
                これまでに引いた玉の数: <%= ballCount %> 個 / 75 個
            </div>
            <ul class="history-list">
                <% for (int i = 0; i < reverseDrawnNumbers.size(); i++) { 
                    int num = reverseDrawnNumbers.get(i);
                    if (i == 0) { %>
                        <li class="history-item first"><%= num %></li>
                    <% } else { %>
                        <li class="history-item"><%= num %></li>
                    <% }
                } %>
            </ul>
        </div>

        <div class="grid-container">
            <div class="panel">
                <h3>👥 参加中のプレイヤー名簿 (<%= game.getAllPlayers().size() %>人)</h3>
                <div style="max-height: 300px; overflow-y: auto;">
                    <ul>
                        <% for (String name : game.getAllPlayers()) { %>
                            <li>• <%= name %></li>
                        <% } 
                           if (game.getAllPlayers().isEmpty()) { %> <p style="color:#888;">まだ誰も参加していません</p> <% } %>
                    </ul>
                </div>
            </div>

            <div class="panel">
                <h3>🏆 ビンゴ達成者一覧</h3>
                <ul id="bingoList">
                    <% 
                       List<PlayerResult> bingoList = game.getBingoPlayers();
                       int totalCount = bingoList.size();
                       
                       for (int i = 0; i < totalCount; i++) {
                           PlayerResult p = bingoList.get(i);
                           int currentRank = totalCount - i; 
                    %>
                        <li><strong><%= currentRank %>位</strong>: <%= p.getPlayerName() %> さん <span style="color:#e63946; font-weight:bold;">(🔑<%= p.getDrawnNumberAtBingo() %>番でビンゴ!)</span></li>
                    <% 
                       } 
                       if (bingoList.isEmpty()) { %> <p style="color:#888;">まだビンゴした人はいません</p> <% } 
                    %>
                </ul>

                <h3 style="margin-top: 25px;">🔥 リーチの人（全自動検知）</h3>
                <ul>
                    <% for (PlayerResult p : game.getReachPlayers()) { %>
                        <li><strong><%= p.getPlayerName() %> さん</strong> <span style="color: #ff9800; font-size: 14px; font-weight: bold;">（あと <%= game.getWaitNumbers(p.getPlayerName()) %> 番でビンゴ！）</span></li>
                    <% } 
                       if (game.getReachPlayers().isEmpty()) { %> <p style="color:#888;">まだリーチの人はいません</p> <% } %>
                </ul>
            </div>
        </div>
    <% } %>
</div>

</body>
</html>
