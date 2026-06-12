package com.rideshare.matching.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * A driver candidate for matching, with their score
 */
@Data
@AllArgsConstructor
@Builder
public class MatchCandidate {
    private String driverId;
    private Double score;          // 0-1, higher is better
    private Integer eta;            // seconds to pickup
    private Double rating;          // driver rating 1-5
    private Double acceptanceRate;  // percentage

    public MatchCandidate(String driverId, Double score, Integer eta) {
        this.driverId = driverId;
        this.score = score;
        this.eta = eta;
    }
}
