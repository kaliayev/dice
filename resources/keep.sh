#! /bin/bash
keep () { 
  curl -X POST -H "Content-Type: application/json" $DICE_HOST/games/$1/players/$2/keep -d "{\"keepers\": [1, 2, 2, 2, 5]}" -v
}

keep $1 $2
