#! /bin/bash
roll () { 
  curl -X POST -H "Content-Type: application/json" $DICE_HOST/games/$1/players/$2/pass | jq .
}

roll $1 $2
