(
set -eu -o pipefail
live=/opt/dehui/frontend/admin/dist
stage=/opt/dehui/releases/b2-fast-90afeb77
bak=/opt/dehui/backups/frontend-b2-fast-pre-90afeb77
old=43e2b256522944e13a2a5ee67bfdaabacbd71f54ba226a7caba2115b923868e8
next=90afeb7754bda0d790fc07ba456ce3e611ee901ca43216c3b48f960b5d2f78ae
exec 8>/opt/dehui/releases/.frontend-publish.lock
flock -n 8
if [ "$(sha256sum "$live/index.html" | cut -d' ' -f1)" = "$next" ]; then
  echo B2_MODEL_FAST_ALREADY_PUBLISHED
  exit 0
fi
pkg=$(mktemp)
trap 'rm -f "$pkg"' EXIT
curl -fLsS --retry 2 --max-time 45 -o "$pkg" 'https://cdn.jsdelivr.net/gh/shao-xiao/Mini-Program@9881d613747a2402c5b35a16255ddbed4519482e/admin-dist.tar.gz'
echo "1028074db7bf62f5a369700d5acb45d7811bab837a26323e23678c82f05ded17  $pkg" | sha256sum -c -
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
test "$(curl -fsS --max-time 20 'https://wuye.mingda.com.cn/assets/spaces?release=90afeb77' | sha256sum | cut -d' ' -f1)" = "$next"
trap - ERR
echo B2_MODEL_FAST_PUBLISHED
)
