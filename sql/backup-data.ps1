param(
    [Parameter(Mandatory = $true)]
    [string]$User,
    [Parameter(Mandatory = $true)]
    [string]$Password,
    [Parameter(Mandatory = $true)]
    [string]$Database,
    [string]$Host = "127.0.0.1",
    [string]$Port = "3306",
    [string]$OutFile = "sql/data-backup.sql"
)

# 只导出通用配置表(无隐私),会话/消息/复盘表绝不导出
$tables = @("role", "role_phrases", "model_source", "model_config")

$args = @(
    "--host=$Host"
    "--port=$Port"
    "--user=$User"
    "--password=$Password"
    "--databases", $Database
    "--tables"
) + $tables + @(
    "--no-create-info"
    "--skip-comments"
    "--skip-add-locks"
)

$env:MYSQL_PWD = $Password
& mysqldump @args | Out-File -Encoding utf8 -FilePath $OutFile
if ($LASTEXITCODE -eq 0) {
    Write-Host "已导出配置表数据到 $OutFile(注意检查 model_source.api_key 是否需脱敏)"
} else {
    Write-Host "导出失败,请确认 mysqldump 在 PATH 中且参数正确"
}
