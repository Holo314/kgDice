java -Dhistory="$2" -Dtoken="$1" -jar build/libs/kgDice-1.1-all.jar &
_dice_pid=$!
echo "$_dice_pid" > _dice.pid
echo $_dice_pid
