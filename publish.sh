echo  "\"$1"\"

git add .
git commit -m "\"$1"\"
git push

git tag  "$2"
git push origin "$2"

