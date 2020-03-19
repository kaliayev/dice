#! /bin/bash
game_state () { 
  curl $DICE_HOST/games/$1 | jq .
}

game_state $1
