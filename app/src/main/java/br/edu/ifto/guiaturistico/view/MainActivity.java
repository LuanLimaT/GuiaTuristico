package br.edu.ifto.guiaturistico.view;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import br.edu.ifto.guiaturistico.R;

/**
 * Única Activity do app. Ela só hospeda a Toolbar, o BottomNavigationView e o NavHostFragment;
 * cada tela é um Fragment (mapa, câmera e fotos) definido no grafo de navegação.
 */
public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private AppBarConfiguration appBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        NavHostFragment navHostFragment = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        navController = navHostFragment.getNavController();

        // Mapa e Câmera são destinos de nível superior (sem seta de voltar)
        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.mapaFragment, R.id.cameraFragment).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        // navegação customizada: evita saveState/restoreState que trava o MapView do OSMDroid
        bottomNav.setOnItemSelectedListener(item -> {
            int destino = item.getItemId();
            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() == destino) {
                return true; // já está na tela certa
            }
            NavOptions opcoes = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(R.id.mapaFragment, false)
                    .build();
            navController.navigate(destino, null, opcoes);
            return true;
        });

        // mantém o item correto selecionado ao navegar pelo backstack
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();
            if (id == R.id.mapaFragment) {
                bottomNav.getMenu().findItem(R.id.mapaFragment).setChecked(true);
            } else if (id == R.id.cameraFragment) {
                bottomNav.getMenu().findItem(R.id.cameraFragment).setChecked(true);
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
