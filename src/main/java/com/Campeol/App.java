package com.Campeol;

import java.util.Scanner;

import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;
import com.Campeol.subgame.subUI;

public class App {
  public static void main(String[] args) {
    // just test
    Scanner sc = new Scanner(System.in);
    Match match = new Match();

    match.startPlayer('X');
    while (true) {
      CharSequence test = sc.nextLine();
      int c1 = test.charAt(0) - '0';
      int c2 = test.charAt(1) - '0';
      match.makeMove(new Position(c1, c2));
      subUI.printBoard(match);
    }
  }
}
