package com.project.drawguess.dto;


public interface UserStats {
    String getUsername();
    Long getTotal_games_played();
    Integer getHigh_score();
    Long getTotal_accumulated_points();
}