package br.edu.ifto.guiaturistico.viewmodel;

import android.app.Application;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import br.edu.ifto.guiaturistico.model.Foto;
import br.edu.ifto.guiaturistico.model.PontoTuristico;
import br.edu.ifto.guiaturistico.repositorio.PontoTuristicoRepositorio;

/**
 * ViewModel da tela da câmera. Aqui fica a regra principal do app:
 * descobrir se o usuário está dentro do raio de algum ponto turístico.
 *
 *  - localizacao: última posição recebida do GPS (enviada pelo fragment)
 *  - pontoAtual:  ponto turístico em cujo raio o usuário está, ou null se estiver fora de todos
 *
 * pontoAtual é recalculado sozinho quando a localização muda OU quando a lista de pontos muda
 * (por exemplo, logo depois de cadastrar um ponto novo).
 */
public class CameraViewModel extends AndroidViewModel {

    public static final float RAIO_MINIMO = 10;
    public static final float RAIO_MAXIMO = 5000;

    private final PontoTuristicoRepositorio repositorio;
    private final MutableLiveData<Location> localizacao = new MutableLiveData<>();
    private final MediatorLiveData<PontoTuristico> pontoAtual = new MediatorLiveData<>();

    public CameraViewModel(@NonNull Application application) {
        super(application);
        repositorio = PontoTuristicoRepositorio.getInstancia(application);

        pontoAtual.addSource(localizacao, novaLocalizacao -> recalcularPontoAtual());
        pontoAtual.addSource(repositorio.getPontos(), lista -> recalcularPontoAtual());
    }

    public LiveData<Location> getLocalizacao() { return localizacao; }
    public LiveData<PontoTuristico> getPontoAtual() { return pontoAtual; }

    public void atualizarLocalizacao(Location novaLocalizacao) {
        localizacao.setValue(novaLocalizacao);
    }

    /**
     * Procura o ponto turístico que contém a posição atual.
     * Se a posição estiver no raio de mais de um ponto, escolhe o de centro mais próximo.
     */
    private void recalcularPontoAtual() {
        Location posicao = localizacao.getValue();
        List<PontoTuristico> pontos = repositorio.getPontos().getValue();
        if (posicao == null || pontos == null) {
            pontoAtual.setValue(null);
            return;
        }

        PontoTuristico encontrado = null;
        float menorDistancia = Float.MAX_VALUE;
        for (PontoTuristico ponto : pontos) {
            float distancia = ponto.distanciaAte(posicao.getLatitude(), posicao.getLongitude());
            if (distancia <= ponto.getRaio() && distancia < menorDistancia) {
                encontrado = ponto;
                menorDistancia = distancia;
            }
        }
        pontoAtual.setValue(encontrado);
    }

    //Ponto turístico mais próximo, mesmo que o usuário esteja fora do raio dele
    public PontoTuristico getPontoMaisProximo() {
        Location posicao = localizacao.getValue();
        List<PontoTuristico> pontos = repositorio.getPontos().getValue();
        if (posicao == null || pontos == null) return null;

        PontoTuristico maisProximo = null;
        float menorDistancia = Float.MAX_VALUE;
        for (PontoTuristico ponto : pontos) {
            float distancia = ponto.distanciaAte(posicao.getLatitude(), posicao.getLongitude());
            if (distancia < menorDistancia) {
                maisProximo = ponto;
                menorDistancia = distancia;
            }
        }
        return maisProximo;
    }

    /** Distância (m) da posição atual até o centro de um ponto. -1 se ainda não há GPS. */
    public float distanciaAte(PontoTuristico ponto) {
        Location posicao = localizacao.getValue();
        if (posicao == null || ponto == null) return -1;
        return ponto.distanciaAte(posicao.getLatitude(), posicao.getLongitude());
    }

    /** Cadastra um ponto turístico novo com centro na posição atual do usuário. */
    public PontoTuristico cadastrarPonto(String nome, float raio) {
        Location posicao = localizacao.getValue();
        if (posicao == null) return null;
        return repositorio.cadastrar(nome, posicao.getLatitude(), posicao.getLongitude(), raio);
    }

    public void adicionarFoto(long idPonto, String caminho, double latitude, double longitude) {
        repositorio.adicionarFoto(idPonto, new Foto(caminho, System.currentTimeMillis(), latitude, longitude));
    }
}
