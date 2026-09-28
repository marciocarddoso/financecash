package br.com.financecash.application.service;

import br.com.financecash.application.dto.CategoryDTO;
import br.com.financecash.application.dto.CategoryUpdateRequest;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.Category;
import br.com.financecash.domain.model.CategoryType;
import br.com.financecash.domain.repository.CategoryRepository;
import br.com.financecash.exception.BusinessException;
import br.com.financecash.exception.ResourceNotFoundException;
import br.com.financecash.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private CategoryService service() {
        return new CategoryService(categoryRepository, currentUserProvider);
    }

    private AppUser user() {
        return AppUser.builder().id(UUID.randomUUID()).email("marcio@example.com").name("Marcio").build();
    }

    @Test
    void deveAtualizarNomeTipoECorDaCategoria() {
        AppUser user = user();
        UUID categoryId = UUID.randomUUID();
        Category category = Category.builder()
                .id(categoryId)
                .owner(user)
                .name("Despesas Variáveis")
                .type(CategoryType.DESPESA)
                .colorHex("#111111")
                .active(true)
                .build();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryDTO dto = service().update(categoryId, new CategoryUpdateRequest("Mercado", CategoryType.DESPESA, "#22C55E"));

        assertThat(dto.name()).isEqualTo("Mercado");
        assertThat(dto.colorHex()).isEqualTo("#22C55E");
    }

    @Test
    void deveRecusarAtualizacaoDeCategoriaDeOutroUsuario() {
        AppUser user = user();
        AppUser outroUsuario = user();
        UUID categoryId = UUID.randomUUID();
        Category category = Category.builder()
                .id(categoryId)
                .owner(outroUsuario)
                .name("Mercado")
                .type(CategoryType.DESPESA)
                .active(true)
                .build();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> service().update(categoryId, new CategoryUpdateRequest("Mercado", CategoryType.DESPESA, "#22C55E")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarNotFoundAoAtualizarCategoriaInexistente() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(categoryId, new CategoryUpdateRequest("Mercado", CategoryType.DESPESA, "#22C55E")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
