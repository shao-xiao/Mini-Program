(
set -eu -o pipefail
live=/opt/dehui/frontend/admin/dist
stage=/opt/dehui/releases/b2-room-modules-e0cfb23c
bak=/opt/dehui/backups/frontend-b2-rooms-pre-e0cfb23c
old=43e2b256522944e13a2a5ee67bfdaabacbd71f54ba226a7caba2115b923868e8
next=e0cfb23ccbc26f32d5de79ff91ad597da04d77c0234a64d8cd21a9667482bbb3
exec 8>/opt/dehui/releases/.frontend-publish.lock
flock -n 8
if [ "$(sha256sum "$live/index.html" | cut -d' ' -f1)" = "$next" ]; then
  echo B2_ROOM_MODULES_ALREADY_PUBLISHED
  exit 0
fi
pkg=$(mktemp)
trap 'rm -f "$pkg"' EXIT
curl -fLsS --retry 2 --max-time 45 -o "$pkg" 'https://cdn.jsdelivr.net/gh/shao-xiao/Mini-Program@5a0d82c3f027bd561296cdff9204c2d5631318f9/admin-dist.tar.gz'
echo "d5f0c075217b2c234b144819abf2dedcc9baef79fa88261f40d059bb67236a30  $pkg" | sha256sum -c -
mkdir -p "$stage"
cd "$stage"
tar xzf "$pkg"
test "$(wc -l <SHA256SUMS)" -eq 96
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
test "$(curl -fsS --max-time 20 'https://wuye.mingda.com.cn/assets/spaces?release=e0cfb23c' | sha256sum | cut -d' ' -f1)" = "$next"
trap - ERR
echo B2_ROOM_MODULES_PUBLISHED
)
