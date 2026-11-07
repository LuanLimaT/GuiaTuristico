package br.edu.ifto.guiaturistico.repositorio;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.edu.ifto.guiaturistico.model.Foto;
import br.edu.ifto.guiaturistico.model.PontoTuristico;

/**
 * Fonte única dos dados do app. Guarda os pontos turísticos (e as fotos de cada um)
 * num arquivo JSON dentro do aparelho e entrega a lista para os ViewModels via LiveData.
 *
 * É um singleton: todas as telas enxergam a MESMA lista, então quando a tela da câmera
 * cadastra um ponto, o mapa já recebe a atualização automaticamente.
 *
 * Regra de threads:
 *  - a lista em memória só é alterada na thread principal;
 *  - ler e gravar o arquivo acontece numa thread separada (executor).
 */
public class PontoTuristicoRepositorio {

    private static final String TAG = "PontoRepositorio";
    private static final String NOME_ARQUIVO = "pontos_turisticos.json";

    private static PontoTuristicoRepositorio instancia;

    private final File arquivo;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler threadPrincipal = new Handler(Looper.getMainLooper());

    private final List<PontoTuristico> lista = new ArrayList<>();
    private final MutableLiveData<List<PontoTuristico>> pontos = new MutableLiveData<>();

    public static synchronized PontoTuristicoRepositorio getInstancia(Context context) {
        if (instancia == null)
            instancia = new PontoTuristicoRepositorio(context.getApplicationContext());
        return instancia;
    }

    private PontoTuristicoRepositorio(Context context) {
        arquivo = new File(context.getFilesDir(), NOME_ARQUIVO);
        carregarDoArquivo();
    }

    public LiveData<List<PontoTuristico>> getPontos() {
        return pontos;
    }

    public PontoTuristico buscarPorId(long id) {
        for (PontoTuristico ponto : lista)
            if (ponto.getId() == id) return ponto;
        return null;
    }

    /** Cadastra um novo ponto turístico. Deve ser chamado na thread principal. */
    public PontoTuristico cadastrar(String nome, double latitude, double longitude, float raio) {
        PontoTuristico ponto = new PontoTuristico(System.currentTimeMillis(), nome, latitude, longitude, raio);
        lista.add(ponto);
        publicarESalvar();
        return ponto;
    }

    /** Adiciona uma foto a um ponto turístico. Deve ser chamado na thread principal. */
    public void adicionarFoto(long idPonto, Foto foto) {
        PontoTuristico ponto = buscarPorId(idPonto);
        if (ponto == null) return;
        ponto.getFotos().add(foto);
        publicarESalvar();
    }

    //  Leitura e gravação

    private void carregarDoArquivo() {
        executor.execute(() -> {
            List<PontoTuristico> lidos;
            boolean primeiraVez = !arquivo.exists();
            if (primeiraVez)
                lidos = pontosIniciais();
            else
                lidos = lerArquivo();

            threadPrincipal.post(() -> {
                lista.addAll(0, lidos); // preserva algo cadastrado antes da leitura terminar
                if (primeiraVez) publicarESalvar();
                else pontos.setValue(new ArrayList<>(lista));
            });
        });
    }

    private void publicarESalvar() {
        // uma lista NOVA a cada mudança, para os observadores perceberem a atualização
        pontos.setValue(new ArrayList<>(lista));

        // o JSON é montado aqui (thread principal, lista consistente) e gravado em segundo plano
        String json = paraJson(lista);
        executor.execute(() -> {
            try (FileWriter escritor = new FileWriter(arquivo)) {
                escritor.write(json);
            } catch (Exception e) {
                Log.e(TAG, "Erro ao salvar os pontos turísticos", e);
            }
        });
    }

    private List<PontoTuristico> lerArquivo() {
        List<PontoTuristico> lidos = new ArrayList<>();
        try (BufferedReader buffer = new BufferedReader(new FileReader(arquivo))) {
            StringBuilder dadosJson = new StringBuilder();
            String linha;
            while ((linha = buffer.readLine()) != null)
                dadosJson.append(linha);

            JSONArray arrayPontos = new JSONArray(dadosJson.toString());
            for (int i = 0; i < arrayPontos.length(); i++) {
                JSONObject objPonto = arrayPontos.getJSONObject(i);
                PontoTuristico ponto = new PontoTuristico(
                        objPonto.getLong("id"),
                        objPonto.getString("nome"),
                        objPonto.getDouble("latitude"),
                        objPonto.getDouble("longitude"),
                        (float) objPonto.getDouble("raio"));

                JSONArray arrayFotos = objPonto.getJSONArray("fotos");
                for (int j = 0; j < arrayFotos.length(); j++) {
                    JSONObject objFoto = arrayFotos.getJSONObject(j);
                    String caminho = objFoto.getString("caminho");
                    if (!new File(caminho).exists()) continue; // foto apagada do aparelho
                    ponto.getFotos().add(new Foto(
                            caminho,
                            objFoto.getLong("dataHora"),
                            objFoto.getDouble("latitude"),
                            objFoto.getDouble("longitude")));
                }
                lidos.add(ponto);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao ler os pontos turísticos", e);
        }
        return lidos;
    }

    private String paraJson(List<PontoTuristico> pontosParaSalvar) {
        JSONArray arrayPontos = new JSONArray();
        try {
            for (PontoTuristico ponto : pontosParaSalvar) {
                JSONObject objPonto = new JSONObject();
                objPonto.put("id", ponto.getId());
                objPonto.put("nome", ponto.getNome());
                objPonto.put("latitude", ponto.getLatitude());
                objPonto.put("longitude", ponto.getLongitude());
                objPonto.put("raio", ponto.getRaio());

                JSONArray arrayFotos = new JSONArray();
                for (Foto foto : ponto.getFotos()) {
                    JSONObject objFoto = new JSONObject();
                    objFoto.put("caminho", foto.getCaminho());
                    objFoto.put("dataHora", foto.getDataHora());
                    objFoto.put("latitude", foto.getLatitude());
                    objFoto.put("longitude", foto.getLongitude());
                    arrayFotos.put(objFoto);
                }
                objPonto.put("fotos", arrayFotos);
                arrayPontos.put(objPonto);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao montar o JSON", e);
        }
        return arrayPontos.toString();
    }

    // Ponto já cadastrado na primeira vez que o app é aberto
    private List<PontoTuristico> pontosIniciais() {
        List<PontoTuristico> iniciais = new ArrayList<>();
        iniciais.add(new PontoTuristico(1, "Praça dos Girassóis", -10.18444, -48.33361, 350));
        return iniciais;
    }
}
