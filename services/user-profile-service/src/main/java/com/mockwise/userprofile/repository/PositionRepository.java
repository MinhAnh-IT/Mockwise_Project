package com.mockwise.userprofile.repository;

import com.mockwise.userprofile.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, String> {
    
    @Query("SELECT p FROM Position p " +
           "JOIN FETCH p.track t " +
           "JOIN FETCH p.level l " +
           "WHERE t.id = :trackId AND l.id = :levelId")
    Optional<Position> findByTrackIdAndLevelId(@Param("trackId") String trackId, 
                                                 @Param("levelId") String levelId);
    
    @Query("SELECT p FROM Position p " +
           "JOIN FETCH p.track " +
           "JOIN FETCH p.level " +
           "WHERE p.positionId = :positionId")
    Optional<Position> findByIdWithDetails(@Param("positionId") String positionId);
    
    @Query("SELECT p FROM Position p " +
           "JOIN FETCH p.track " +
           "JOIN FETCH p.level")
    List<Position> findAllWithDetails();
    
    boolean existsByTrack_IdAndLevel_Id(String trackId, String levelId);
}
