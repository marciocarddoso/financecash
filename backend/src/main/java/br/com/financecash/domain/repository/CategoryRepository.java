package br.com.financecash.domain.repository;

import br.com.financecash.domain.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findByOwnerIdAndActiveTrueOrderByNameAsc(UUID ownerId);

    /** Usado pela importação em lote (EntryImportService) para resolver/criar categoria por nome. */
    Optional<Category> findByOwnerIdAndNameIgnoreCase(UUID ownerId, String name);
}
