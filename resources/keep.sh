#! /bin/bash
keep () { 
  curl -X POST -H "Content-Type: application/json" $DICE_HOST/games/$1/players/$2/keep -d "{\"keepers\": [5]}" | jq .
}

keep $1 $2
