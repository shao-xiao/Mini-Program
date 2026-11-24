(
set -eu -o pipefail
live=/opt/dehui/frontend/admin/dist
stage=/opt/dehui/releases/b2-water-rooms-43e2b256
bak=/opt/dehui/backups/frontend-b2-water-pre-43e2b256
old=26a6fa82cf8d1a0608f4f29b1514c81d9467f69f058f9c292fcdf617eec74832
next=43e2b256522944e13a2a5ee67bfdaabacbd71f54ba226a7caba2115b923868e8
exec 8>/opt/dehui/releases/.frontend-publish.lock
flock -n 8
if [ "$(sha256sum "$live/index.html" | cut -d' ' -f1)" = "$next" ]; then
  echo B2_WATER_ROOMS_ALREADY_PUBLISHED
  exit 0
fi
pkg=$(mktemp)
trap 'rm -f "$pkg"' EXIT
curl -fLsS --retry 2 --max-time 45 -o "$pkg" 'https://cdn.jsdelivr.net/gh/shao-xiao/Mini-Program@d508123ab67953093ca1c52b01e54c0359917a36/admin-dist.tar.gz'
echo "262913f14ab283f383b5dbfdb800eb20c131b329e72d01cedf4fb154ed4add94  $pkg" | sha256sum -c -
mkdir -p "$stage"
cd "$stage"
tar xzf "$pkg"
test "$(wc -l <SHA256SUMS)" -eq 94
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
test "$(curl -fsS --max-time 20 'https://wuye.mingda.com.cn/assets/spaces?release=43e2b256' | sha256sum | cut -d' ' -f1)" = "$next"
trap - ERR
echo B2_WATER_ROOMS_PUBLISHED
)
