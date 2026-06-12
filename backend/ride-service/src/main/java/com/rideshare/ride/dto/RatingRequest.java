package com.rideshare.ride.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for rating a completed ride.
 * Allows both rider and driver to rate each other and provide feedback.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RatingRequest {

    @NotBlank(message = "Rater ID is required")
    private String raterId;

    @NotBlank(message = "Rater type is required (RIDER or DRIVER)")
    private String raterType;

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot exceed 5")
    private Integer rating;

    @Size(max = 500, message = "Feedback cannot exceed 500 characters")
    private String feedback;
}
