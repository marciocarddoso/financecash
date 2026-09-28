package br.com.financecash.application.service;

import br.com.financecash.application.dto.CategoryCreateRequest;
import br.com.financecash.application.dto.CategoryDTO;
import br.com.financecash.application.dto.CategoryUpdateRequest;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.Category;
import br.com.financecash.domain.repository.CategoryRepository;
import br.com.financecash.exception.BusinessException;
import br.com.financecash.exception.ResourceNotFoundException;
import br.com.financecash.security.CurrentUserProvider;
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
    public CategoryDTO update(UUID categoryId, CategoryUpdateRequest request) {
        AppUser user = currentUserProvider.getCurrentUser();
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada: " + categoryId));
        if (!category.getOwner().getId().equals(user.getId())) {
            throw new BusinessException("Esta categoria não pertence ao usuário logado.");
        }
        category.setName(request.name());
        category.setType(request.type());
        category.setColorHex(request.colorHex());
        return CategoryDTO.from(categoryRepository.save(category));
    }

    @Transactional
    public void deactivate(UUID categoryId) {
        AppUser user = currentUserProvider.getCurrentUser();
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada: " + categoryId));
        if (!category.getOwner().getId().equals(user.getId())) {
            throw new BusinessException("Esta categoria não pertence ao usuário logado.");
        }
        category.setActive(false);
    }
}
