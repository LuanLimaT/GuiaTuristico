package br.edu.ifto.guiaturistico.view;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.infowindow.InfoWindow;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import br.edu.ifto.guiaturistico.R;
import br.edu.ifto.guiaturistico.model.PontoTuristico;
import br.edu.ifto.guiaturistico.viewmodel.MapaViewModel;

/**
 * Tela inicial: mapa com todos os pontos turísticos cadastrados.
 * Cada ponto aparece como um marcador e um círculo mostrando o seu raio.
 * Clicar no marcador abre as fotos do ponto; o botão "Tirar fotos" abre a câmera.
 *
 * Tocar no mapa define a posição atual (marcador vermelho),
 * usada pela tela da câmera para cadastrar/fotografar pontos.
 */
public class MapaFragment extends Fragment {

    private static final int CODIGO_PERMISSAO_LOCALIZACAO = 1;

    private MapaViewModel viewModel;
    private MapView mapView;
    private MyLocationNewOverlay minhaLocalizacaoOverlay;
    private TextView tvResumo;
    private MaterialButton btLimparLocalizacao;

    // marcador da posição atual (posicionado pelo toque do usuário)
    private Marker marcadorPosicaoAtual;

    // overlays dos pontos turísticos (guardados para poder redesenhar quando a lista mudar)
    private final List<Overlay> overlaysDosPontos = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // precisa ser configurado ANTES de criar o MapView (slide do OSMBonusPack)
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());
        // cache dos blocos do mapa na pasta do próprio app (não precisa de permissão de escrita)
        File pastaMapa = new File(requireContext().getCacheDir(), "osmdroid");
        Configuration.getInstance().setOsmdroidBasePath(pastaMapa);
        Configuration.getInstance().setOsmdroidTileCache(new File(pastaMapa, "tiles"));
        return inflater.inflate(R.layout.fragment_mapa, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ViewModel no escopo da ACTIVITY para compartilhar a posição atual com a câmera
        viewModel = new ViewModelProvider(requireActivity()).get(MapaViewModel.class);
        tvResumo = view.findViewById(R.id.tvResumo);

        configurarMapa(view);

        ExtendedFloatingActionButton fabTirarFotos = view.findViewById(R.id.fabTirarFotos);
        fabTirarFotos.setOnClickListener(v -> navegar(R.id.action_mapa_para_camera, null));

        FloatingActionButton fabMinhaLocalizacao = view.findViewById(R.id.fabMinhaLocalizacao);
        fabMinhaLocalizacao.setOnClickListener(v -> centralizarNaMinhaLocalizacao());

        btLimparLocalizacao = view.findViewById(R.id.btLimparLocalizacao);
        btLimparLocalizacao.setOnClickListener(v -> limparPosicaoAtual());

        // sempre que a lista de pontos mudar, o mapa é redesenhado
        viewModel.getPontos().observe(getViewLifecycleOwner(), this::desenharPontos);

        // restaura o marcador da posição atual se já existia
        viewModel.getPosicaoAtual().observe(getViewLifecycleOwner(), loc -> {
            if (loc != null) {
                colocarMarcadorPosicaoAtual(new GeoPoint(loc.getLatitude(), loc.getLongitude()), false);
                btLimparLocalizacao.setVisibility(View.VISIBLE);
            } else {
                removerMarcadorPosicaoAtual();
                btLimparLocalizacao.setVisibility(View.GONE);
            }
        });

        // pede a localização uma vez só (não insiste toda vez que o usuário volta ao mapa)
        if (!temPermissaoLocalizacao() && !viewModel.isPermissaoJaPedida()) {
            viewModel.setPermissaoJaPedida(true);
            pedirPermissaoLocalizacao();
        }
    }

    // ===== MAPA =====

    private void configurarMapa(View view) {
        mapView = view.findViewById(R.id.mapview);
        mapView.setMultiTouchControls(true);
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);

        IMapController mapController = mapView.getController();
        mapController.setZoom(viewModel.getZoom());
        mapController.setCenter(new GeoPoint(viewModel.getCentroLatitude(), viewModel.getCentroLongitude()));

        // detecta toques no mapa para posicionar o marcador da posição atual
        // adicionado PRIMEIRO (índice 0) para que receba eventos POR ÚLTIMO,
        // permitindo que marcadores dos pontos turísticos tratem o clique antes
        MapEventsOverlay toqueOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                colocarMarcadorPosicaoAtual(p, true);
                viewModel.definirPosicaoAtual(p.getLatitude(), p.getLongitude());
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        });
        mapView.getOverlays().add(0, toqueOverlay);

        // bolinha azul com a posição do usuário (OSMDroid)
        minhaLocalizacaoOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(requireContext()), mapView);
        mapView.getOverlays().add(minhaLocalizacaoOverlay);
    }

    // ===== MARCADOR POSIÇÃO ATUAL =====

    private void colocarMarcadorPosicaoAtual(GeoPoint posicao, boolean mostrarFeedback) {
        if (marcadorPosicaoAtual == null) {
            marcadorPosicaoAtual = new Marker(mapView);
            marcadorPosicaoAtual.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marcadorPosicaoAtual.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_marcador_posicao_atual));
            marcadorPosicaoAtual.setInfoWindow(null);
            mapView.getOverlays().add(marcadorPosicaoAtual);
        }
        marcadorPosicaoAtual.setPosition(posicao);
        marcadorPosicaoAtual.setTitle(getString(R.string.posicao_atual_ativa,
                posicao.getLatitude(), posicao.getLongitude()));
        mapView.invalidate();

        btLimparLocalizacao.setVisibility(View.VISIBLE);

        if (mostrarFeedback && getView() != null) {
            Snackbar.make(getView(), R.string.posicao_atual_definida, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void removerMarcadorPosicaoAtual() {
        if (marcadorPosicaoAtual != null) {
            mapView.getOverlays().remove(marcadorPosicaoAtual);
            marcadorPosicaoAtual = null;
            mapView.invalidate();
        }
    }

    private void limparPosicaoAtual() {
        viewModel.limparPosicaoAtual();
        if (getView() != null) {
            Snackbar.make(getView(), R.string.posicao_atual_limpa, Snackbar.LENGTH_SHORT).show();
        }
    }

    // ===== PONTOS TURÍSTICOS =====

    private void desenharPontos(List<PontoTuristico> pontos) {
        mapView.getOverlays().removeAll(overlaysDosPontos);
        overlaysDosPontos.clear();

        for (PontoTuristico ponto : pontos) {
            GeoPoint centro = new GeoPoint(ponto.getLatitude(), ponto.getLongitude());

            // círculo mostrando o raio do ponto turístico
            Polygon circulo = new Polygon(mapView);
            circulo.setPoints(Polygon.pointsAsCircle(centro, ponto.getRaio()));
            circulo.setFillColor(ContextCompat.getColor(requireContext(), R.color.raio_preenchimento));
            circulo.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.raio_borda));
            circulo.setStrokeWidth(4);
            circulo.setInfoWindow((InfoWindow) null);
            overlaysDosPontos.add(circulo);

            // marcador do ponto turístico
            Marker marcador = new Marker(mapView);
            marcador.setPosition(centro);
            marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marcador.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_marcador_ponto));
            marcador.setTitle(ponto.getNome());
            marcador.setOnMarkerClickListener((m, mv) -> {
                abrirFotos(ponto);
                return true;
            });
            overlaysDosPontos.add(marcador);
        }

        // círculos e marcadores ficam ACIMA do MapEventsOverlay (índice 0)
        // mas ABAIXO da bolinha de "minha localização", para que os marcadores
        // recebam o clique antes do MapEventsOverlay
        mapView.getOverlays().addAll(1, overlaysDosPontos);
        mapView.invalidate();

        tvResumo.setText(pontos.isEmpty()
                ? getString(R.string.mapa_sem_pontos)
                : getResources().getQuantityString(R.plurals.mapa_resumo, pontos.size(), pontos.size()));
    }

    private void abrirFotos(PontoTuristico ponto) {
        Bundle argumentos = new Bundle();
        argumentos.putLong(FotosFragment.ARG_ID_PONTO, ponto.getId());
        argumentos.putString(FotosFragment.ARG_NOME_PONTO, ponto.getNome());
        navegar(R.id.action_mapa_para_fotos, argumentos);
    }

    //Navega só se o mapa ainda for a tela atual (evita erro com dois toques rápidos).
    private void navegar(int idAcao, Bundle argumentos) {
        NavController navController = NavHostFragment.findNavController(this);
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.mapaFragment)
            navController.navigate(idAcao, argumentos);
    }

    private void centralizarNaMinhaLocalizacao() {
        if (!temPermissaoLocalizacao()) {
            pedirPermissaoLocalizacao();
            return;
        }
        GeoPoint minhaPosicao = minhaLocalizacaoOverlay.getMyLocation();
        if (minhaPosicao == null) {
            Toast.makeText(requireContext(), R.string.aguardando_gps, Toast.LENGTH_SHORT).show();
            return;
        }
        mapView.getController().animateTo(minhaPosicao);
    }

    //Ciclo de vida (o MapView precisa ser avisado)

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        if (temPermissaoLocalizacao())
            minhaLocalizacaoOverlay.enableMyLocation();
    }

    @Override
    public void onPause() {
        super.onPause();
        // guarda posição e zoom para quando o usuário voltar para o mapa
        viewModel.salvarPosicaoDoMapa(
                mapView.getMapCenter().getLatitude(),
                mapView.getMapCenter().getLongitude(),
                mapView.getZoomLevelDouble());
        minhaLocalizacaoOverlay.disableMyLocation();
        mapView.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        marcadorPosicaoAtual = null; // será recriado em onViewCreated se necessário
        mapView.onDetach();
    }

    // ===== PERMISSÃO DE LOCALIZAÇÃO (padrão do slide de Permissões) =====

    private boolean temPermissaoLocalizacao() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void pedirPermissaoLocalizacao() {
        String[] permissoes = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.permissao_titulo)
                    .setMessage(R.string.permissao_localizacao_mapa)
                    .setPositiveButton(R.string.pedir_permissao,
                            (dialog, which) -> requestPermissions(permissoes, CODIGO_PERMISSAO_LOCALIZACAO))
                    .setNegativeButton(R.string.agora_nao, null)
                    .show();
        } else {
            requestPermissions(permissoes, CODIGO_PERMISSAO_LOCALIZACAO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CODIGO_PERMISSAO_LOCALIZACAO) {
            if (temPermissaoLocalizacao())
                minhaLocalizacaoOverlay.enableMyLocation();
            else
                Toast.makeText(requireContext(), R.string.mapa_sem_localizacao, Toast.LENGTH_LONG).show();
        }
    }
}
