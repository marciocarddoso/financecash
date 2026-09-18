package br.com.finkeeper.application.dto;

import br.com.finkeeper.domain.model.Category;
import br.com.finkeeper.domain.model.CategoryType;

import java.util.UUID;

public record CategoryDTO(UUID id, String name, CategoryType type, String colorHex, boolean active) {
    public static CategoryDTO from(Category category) {
        return new CategoryDTO(category.getId(), category.getName(), category.getType(), category.getColorHex(), category.isActive());
    }
}
