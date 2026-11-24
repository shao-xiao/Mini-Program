(
set -eu -o pipefail
live=/opt/dehui/frontend/admin/dist
stage=/opt/dehui/releases/b2-water-25c56a07
bak=/opt/dehui/backups/frontend-b2-water-pre-25c56a07
old=120ebab3d63a114e92ba19992678032dda2844d1116e8f40779c8c5325957bb2
next=25c56a07614c242f930841bec69f6fba60d09890b62aefbeadf504e6be1bc7c6
exec 8>/opt/dehui/releases/.frontend-publish.lock
flock -n 8
pkg=$(mktemp)
trap 'rm -f "$pkg"' EXIT
curl -fLsS --retry 2 --max-time 45 -o "$pkg" 'https://cdn.jsdelivr.net/gh/shao-xiao/Mini-Program@1bec059a8fb80fe5b3f41baf113842923659c395/admin-dist.tar.gz'
echo "a89400d9cfa1bd22fc0ac02ff2ba1221c81d7a9a7546981148d4996529756918  $pkg" | sha256sum -c -
mkdir -p "$stage"
cd "$stage"
tar xzf "$pkg"
test "$(wc -l <SHA256SUMS)" -eq 92
sha256sum --quiet -c SHA256SUMS
echo "$old  $live/index.html" | sha256sum -c -
mkdir -p -m 700 "$bak"
cp -pn "$live/index.html" "$bak/index.html"
echo "$old  $bak/index.html" | sha256sum -c -
rsync -a --ignore-existing --exclude=index.html --exclude=SHA256SUMS ./ "$live/"
(cd "$live"; awk '$2!="./index.html"' "$stage/SHA256SUMS" | sha256sum --quiet -c -)
replace(){ entry=$(mktemp "$live/.index.XXXXXX"); install -m 0644 "$1" "$entry"; mv -fT "$entry" "$live/index.html"; }
replace "$stage/index.html"
trap 'replace "$bak/index.html"' ERR
test "$(curl -fsS --max-time 20 'https://wuye.mingda.com.cn/assets/spaces?release=25c56a07' | sha256sum | cut -d' ' -f1)" = "$next"
trap - ERR
echo B2_WATER_PUBLISHED
)
