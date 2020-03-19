#! /bin/bash
start_game () { 
  curl -X PUT -H "Content-Type: application/json" $DICE_HOST/games/$1/start | jq .
}

start_game $1
