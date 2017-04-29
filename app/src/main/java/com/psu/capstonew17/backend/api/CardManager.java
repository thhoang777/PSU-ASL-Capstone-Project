package com.psu.capstonew17.backend.api;

public interface CardManager {
    /**
     * Create a new card with the specified contents and answer
     */
    Card buildCard(Video video, String answer) throws ObjectAlreadyExistsException;
}
