package br.com.finkeeper.application.service;

import br.com.finkeeper.application.dto.CategoryCreateRequest;
import br.com.finkeeper.application.dto.CategoryDTO;
import br.com.finkeeper.domain.model.AppUser;
import br.com.finkeeper.domain.model.Category;
import br.com.finkeeper.domain.repository.CategoryRepository;
import br.com.finkeeper.exception.ResourceNotFoundException;
import br.com.finkeeper.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CurrentUserProvider currentUserProvider;

    public CategoryService(CategoryRepository categoryRepository, CurrentUserProvider currentUserProvider) {
        this.categoryRepository = categoryRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<CategoryDTO> listActive() {
        AppUser user = currentUserProvider.getCurrentUser();
        return categoryRepository.findByOwnerIdAndActiveTrueOrderByNameAsc(user.getId())
                .stream().map(CategoryDTO::from).toList();
    }

    @Transactional
    public CategoryDTO create(CategoryCreateRequest request) {
        AppUser user = currentUserProvider.getCurrentUser();
        Category category = Category.builder()
                .owner(user)
                .name(request.name())
                .type(request.type())
                .colorHex(request.colorHex())
                .active(true)
                .build();
        return CategoryDTO.from(categoryRepository.save(category));
    }

    @Transactional
    public void deactivate(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada: " + categoryId));
        category.setActive(false);
    }
}
