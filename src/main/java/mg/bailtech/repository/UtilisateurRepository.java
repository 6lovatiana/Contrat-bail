package mg.bailtech.repository;

import mg.bailtech.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Integer> {
    Optional<Utilisateur> findByCinNumero(String cinNumero);
    Optional<Utilisateur> findByEmail(String email);
}