package com.Campeol.net;

import com.Campeol.subgame.Match;
import com.Campeol.subgame.Position;

public class Pack {
  private Match match;
  private Position pos;

  public Pack(Match match, Position pos) {
    this.match = match;
    this.pos = pos;
  }

  public Match getMatch() {
    return match;
  }

  public void setMatch(Match match) {
    this.match = match;
  }

  public Position getPos() {
    return pos;
  }

  public void setPos(Position pos) {
    this.pos = pos;
  }
}
