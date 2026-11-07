package br.edu.ifto.guiaturistico.viewmodel;

import android.app.Application;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.osmdroid.util.GeoPoint;

import java.util.List;

import br.edu.ifto.guiaturistico.model.PontoTuristico;
import br.edu.ifto.guiaturistico.repositorio.PontoTuristicoRepositorio;

/**
 * ViewModel da tela do mapa: entrega os pontos turísticos e lembra onde o mapa estava,
 * para que ao voltar da tela de fotos o mapa continue no mesmo lugar e com o mesmo zoom.
 *
 * Também guarda a localização simulada (definida pelo usuário no mapa) que é compartilhada
 * com a tela da câmera quando este ViewModel é obtido no escopo da Activity.
 */
public class MapaViewModel extends AndroidViewModel {

    private final PontoTuristicoRepositorio repositorio;

    private GeoPoint centro = new GeoPoint(-10.18444, -48.33361); // Palmas - TO (slide)
    private double zoom = 15;
    private boolean permissaoJaPedida = false;

    // localização simulada: o usuário posiciona o crosshair no mapa e clica em "Usar esta localização"
    private final MutableLiveData<Location> localizacaoSimulada = new MutableLiveData<>();

    public MapaViewModel(@NonNull Application application) {
        super(application);
        repositorio = PontoTuristicoRepositorio.getInstancia(application);
    }

    public LiveData<List<PontoTuristico>> getPontos() {
        return repositorio.getPontos();
    }

    public void salvarPosicaoDoMapa(GeoPoint centro, double zoom) {
        this.centro = centro;
        this.zoom = zoom;
    }

    public GeoPoint getCentro() { return centro; }
    public double getZoom() { return zoom; }

    public boolean isPermissaoJaPedida() { return permissaoJaPedida; }
    public void setPermissaoJaPedida(boolean pedida) { permissaoJaPedida = pedida; }

    // ===== LOCALIZAÇÃO SIMULADA =====

    public LiveData<Location> getLocalizacaoSimulada() { return localizacaoSimulada; }

    public void definirLocalizacaoSimulada(double latitude, double longitude) {
        Location loc = new Location("simulada");
        loc.setLatitude(latitude);
        loc.setLongitude(longitude);
        loc.setAccuracy(0);
        localizacaoSimulada.setValue(loc);
    }

    public void limparLocalizacaoSimulada() {
        localizacaoSimulada.setValue(null);
    }
}
