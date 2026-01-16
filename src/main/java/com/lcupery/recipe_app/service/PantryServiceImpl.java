package com.lcupery.recipe_app.service;

import com.lcupery.recipe_app.dto.PantryItemDto;
import com.lcupery.recipe_app.entity.PantryItem;
import com.lcupery.recipe_app.entity.User;
import com.lcupery.recipe_app.exception.ResourceNotFoundException;
import com.lcupery.recipe_app.mapper.PantryItemMapper;
import com.lcupery.recipe_app.repository.PantryItemRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class PantryServiceImpl implements PantryService {

    private PantryItemRepository pantryItemRepository;

    @Override
    public List<PantryItemDto> getAllPantryItems(User user) {
        List<PantryItem> items = pantryItemRepository.findByUserOrderByNameAsc(user);
        return items.stream()
                .map(PantryItemMapper::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PantryItemDto addPantryItem(PantryItemDto pantryItemDto, User user) {
        PantryItem item = PantryItemMapper.mapToEntity(pantryItemDto);
        item.setUser(user);

        try {
            PantryItem saved = pantryItemRepository.save(item);
            log.info("Added pantry item {} to {} for user {}",
                    saved.getName(), saved.getLocation(), user.getAuth0Id());
            return PantryItemMapper.mapToDto(saved);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Item already exists in this location");
        }
    }

    @Override
    @Transactional
    public PantryItemDto updatePantryItem(Long itemId, PantryItemDto pantryItemDto, User user) {
        PantryItem item = pantryItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Pantry item not found: " + itemId));

        // Verify ownership
        if (!item.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Unauthorized access to pantry item");
        }

        item.setName(pantryItemDto.getName());
        item.setLocation(pantryItemDto.getLocation());

        PantryItem updated = pantryItemRepository.save(item);
        log.info("Updated pantry item {} for user {}", itemId, user.getAuth0Id());
        return PantryItemMapper.mapToDto(updated);
    }

    @Override
    @Transactional
    public void deletePantryItem(Long itemId, User user) {
        PantryItem item = pantryItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Pantry item not found: " + itemId));

        // Verify ownership
        if (!item.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Unauthorized access to pantry item");
        }

        pantryItemRepository.delete(item);
        log.info("Deleted pantry item {} for user {}", itemId, user.getAuth0Id());
    }
}
