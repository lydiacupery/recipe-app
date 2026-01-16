package com.lcupery.recipe_app.repository;

import com.lcupery.recipe_app.entity.PantryItem;
import com.lcupery.recipe_app.entity.StorageLocation;
import com.lcupery.recipe_app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PantryItemRepository extends JpaRepository<PantryItem, Long> {
    List<PantryItem> findByUserOrderByNameAsc(User user);
    List<PantryItem> findByUserAndLocation(User user, StorageLocation location);
}
