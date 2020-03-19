#! /bin/bash
add_player () { 
  curl -X POST -H "Content-Type: application/json" $DICE_HOST/games/$1/players -d "{\"name\": \"$2\"}" | jq .
}

add_player $1 $2
