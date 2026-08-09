package com.Campeol;

import java.util.Scanner;
import com.Campeol.MatchStatus;
import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;
import com.Campeol.subgame.subUI;

public class App {
  public static void main(String[] args) {
    // just test
    Scanner sc = new Scanner(System.in);
    Match match = new Match();

    match.startPlayer('X');
    while (match.getMatchStatus() == MatchStatus.IN_PROGRESS) {
      CharSequence test = sc.nextLine();
      int row = test.charAt(0) - '0';
      int column = test.charAt(1) - '0';
      match.makeMove(new Position(row, column));
      subUI.printBoard(match);
    }

    subUI.endGame(match);
  }
}
