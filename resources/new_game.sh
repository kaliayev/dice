#! /bin/bash
new_game () { 
  curl -X POST -H "Content-Type: application/json" $DICE_HOST/games -d "{\"friendly-name\": \"$1\"}" | jq .
}

new_game $1
