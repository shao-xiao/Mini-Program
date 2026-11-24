(
set -eu -o pipefail
live=/opt/dehui/frontend/admin/dist
stage=/opt/dehui/releases/b2-water-26a6fa82
bak=/opt/dehui/backups/frontend-b2-water-pre-26a6fa82
old=25c56a07614c242f930841bec69f6fba60d09890b62aefbeadf504e6be1bc7c6
next=26a6fa82cf8d1a0608f4f29b1514c81d9467f69f058f9c292fcdf617eec74832
exec 8>/opt/dehui/releases/.frontend-publish.lock
flock -n 8
pkg=$(mktemp)
trap 'rm -f "$pkg"' EXIT
curl -fLsS --retry 2 --max-time 45 -o "$pkg" 'https://cdn.jsdelivr.net/gh/shao-xiao/Mini-Program@11fe26132470b07471970dcf521882ba389fabd1/admin-dist.tar.gz'
echo "98aed0932f438e67e506dc42c940ad4c8e49a477fc35642cd4056a81df3e6632  $pkg" | sha256sum -c -
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
test "$(curl -fsS --max-time 20 'https://wuye.mingda.com.cn/assets/spaces?release=26a6fa82' | sha256sum | cut -d' ' -f1)" = "$next"
trap - ERR
echo B2_WATER_V2_PUBLISHED
)
