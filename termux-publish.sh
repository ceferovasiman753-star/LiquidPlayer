#!/data/data/com.termux/files/usr/bin/bash
# Создаёт репозиторий на GitHub, заливает проект, ждёт сборку и кладёт APK в "Загрузки".
# Использование:  bash termux-publish.sh [ИмяРепозитория] [public|private]
set -e
NAME="${1:-LiquidPlayer}"
VIS="${2:-public}"

command -v gh >/dev/null || pkg install -y gh
command -v git >/dev/null || pkg install -y git

if ! gh auth status >/dev/null 2>&1; then
  echo ">> Нужно войти в GitHub (один раз):"
  gh auth login
fi
gh auth setup-git >/dev/null 2>&1 || true

LOGIN="$(gh api user -q .login)"
git config user.name  >/dev/null 2>&1 || git config --global user.name  "$LOGIN"
git config user.email >/dev/null 2>&1 || git config --global user.email "$LOGIN@users.noreply.github.com"

[ -d .git ] || git init -q
git checkout -q -B main
git add -A
git commit -qm "Liquid Player" || true

echo ">> Создаю репозиторий $LOGIN/$NAME ($VIS) и заливаю код..."
gh repo create "$NAME" --"$VIS" --source=. --remote=origin --push

echo ">> Жду запуск сборки на GitHub Actions..."
RUN=""
for i in $(seq 1 30); do
  RUN="$(gh run list -R "$LOGIN/$NAME" --limit 1 --json databaseId -q '.[0].databaseId' 2>/dev/null || true)"
  [ -n "$RUN" ] && break
  sleep 4
done
[ -n "$RUN" ] || { echo "Сборка не запустилась. Открой вкладку Actions в репозитории."; exit 1; }

if gh run watch "$RUN" -R "$LOGIN/$NAME" --exit-status; then
  OUT="$HOME/storage/downloads/LiquidPlayer-apk"
  mkdir -p "$OUT"
  gh run download "$RUN" -R "$LOGIN/$NAME" -n LiquidPlayer-apk -D "$OUT"
  echo
  echo "ГОТОВО! APK лежит в: Загрузки/LiquidPlayer-apk/LiquidPlayer.apk"
  echo "Открой его через файловый менеджер и установи."
else
  echo
  echo "Сборка упала. Скопируй этот текст и отправь мне:"
  echo "-------------------------------------------------"
  gh run view "$RUN" -R "$LOGIN/$NAME" --log-failed | tail -n 60
  exit 1
fi
