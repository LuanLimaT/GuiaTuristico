package br.edu.ifto.guiaturistico.viewmodel;

import android.app.Application;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import br.edu.ifto.guiaturistico.model.PontoTuristico;
import br.edu.ifto.guiaturistico.repositorio.PontoTuristicoRepositorio;

/**
 * ViewModel da tela do mapa: entrega os pontos turísticos e lembra onde o mapa estava,
 * para que ao voltar da tela de fotos o mapa continue no mesmo lugar e com o mesmo zoom.
 *
 * Também guarda a posição atual (definida pelo usuário no mapa) que é compartilhada
 * com a tela da câmera quando este ViewModel é obtido no escopo da Activity.
 */
public class MapaViewModel extends AndroidViewModel {

    private final PontoTuristicoRepositorio repositorio;

    // posição central do mapa (Palmas - TO como padrão inicial)
    private double centroLatitude = -10.18444;
    private double centroLongitude = -48.33361;
    private double zoom = 15;
    private boolean permissaoJaPedida = false;

    // posição atual: o usuário posiciona o marcador no mapa
    private final MutableLiveData<Location> posicaoAtual = new MutableLiveData<>();

    public MapaViewModel(@NonNull Application application) {
        super(application);
        repositorio = PontoTuristicoRepositorio.getInstancia(application);
    }

    public LiveData<List<PontoTuristico>> getPontos() {
        return repositorio.getPontos();
    }

    public void salvarPosicaoDoMapa(double latitude, double longitude, double zoom) {
        this.centroLatitude = latitude;
        this.centroLongitude = longitude;
        this.zoom = zoom;
    }

    public double getCentroLatitude() { return centroLatitude; }
    public double getCentroLongitude() { return centroLongitude; }
    public double getZoom() { return zoom; }

    public boolean isPermissaoJaPedida() { return permissaoJaPedida; }
    public void setPermissaoJaPedida(boolean pedida) { permissaoJaPedida = pedida; }

    // Posição Atual
    public LiveData<Location> getPosicaoAtual() { return posicaoAtual; }

    public void definirPosicaoAtual(double latitude, double longitude) {
        Location loc = new Location("posicao_atual");
        loc.setLatitude(latitude);
        loc.setLongitude(longitude);
        loc.setAccuracy(0);
        posicaoAtual.setValue(loc);
    }

    public void limparPosicaoAtual() {
        posicaoAtual.setValue(null);
    }
}
