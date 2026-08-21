<html>
<head>
    <style>
        body {
            font-family: 'SimSun';
        }

        /* 必须指定字体 */
        .header {
            text-align: center;
            font-size: 24px;
            font-weight: bold;
        }

        table {
            width: 100%;
            border-collapse: collapse;
        }

        td, th {
            border: 1px solid black;
            padding: 8px;
        }
    </style>
</head>
<body>
<div class="header">固定资产报废交接单</div>
<p>单号：${code}</p>

<table>
    <tr>
        <th>资产名称</th>
        <td>${assetName}</td>
    </tr>
    <tr>
        <th>审批记录</th>
        <td>
            <!-- 循环遍历 Freemarker 语法 -->
            <#list approvers as log>
                <div>${log.approver} - ${log.status} (${log.comment}) - ${log.date}</div>
            </#list>
        </td>
    </tr>
</table>

<div style="margin-top: 50px;">
    签字盖章：_________________
</div>
</body>
</html>
