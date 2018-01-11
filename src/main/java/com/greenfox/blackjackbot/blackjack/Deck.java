package com.greenfox.blackjackbot.blackjack;

import java.util.ArrayList;

public class Deck {

  private ArrayList<Card> deck;

  public Deck() {
    deck = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j <= 12; j++) {
        deck.add(new Card(i, j));
      }
    }
  }

  public void shuffle() {
    java.util.Collections.shuffle(this.deck);
  }

  public Card drawCard() {
    return deck.remove(0);
  }
}