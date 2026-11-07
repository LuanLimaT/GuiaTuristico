package br.edu.ifto.guiaturistico.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import br.edu.ifto.guiaturistico.model.PontoTuristico;
import br.edu.ifto.guiaturistico.repositorio.PontoTuristicoRepositorio;

/**
 * ViewModel da tela de fotos: acompanha UM ponto turístico específico.
 * Sempre que a lista do repositório muda (ex.: uma foto nova), o ponto é atualizado.
 */
public class FotosViewModel extends AndroidViewModel {

    private final PontoTuristicoRepositorio repositorio;
    private final MediatorLiveData<PontoTuristico> ponto = new MediatorLiveData<>();
    private boolean carregado = false;

    public FotosViewModel(@NonNull Application application) {
        super(application);
        repositorio = PontoTuristicoRepositorio.getInstancia(application);
    }

    public void carregar(long idPonto) {
        if (carregado) return; // a tela pode ser recriada (ex.: girar o celular)
        carregado = true;
        ponto.addSource(repositorio.getPontos(), lista -> ponto.setValue(repositorio.buscarPorId(idPonto)));
    }

    public LiveData<PontoTuristico> getPonto() {
        return ponto;
    }
}
