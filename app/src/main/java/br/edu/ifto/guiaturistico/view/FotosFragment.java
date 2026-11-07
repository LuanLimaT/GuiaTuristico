package br.edu.ifto.guiaturistico.view;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.edu.ifto.guiaturistico.R;
import br.edu.ifto.guiaturistico.adapter.FotoAdapter;
import br.edu.ifto.guiaturistico.model.Foto;
import br.edu.ifto.guiaturistico.util.ImagemUtil;
import br.edu.ifto.guiaturistico.viewmodel.FotosViewModel;

/**
 * Mostra todas as fotos cadastradas de um ponto turístico.
 * Recebe pelo Bundle o id do ponto (e o nome, usado no título da Toolbar).
 */
public class FotosFragment extends Fragment {

    public static final String ARG_ID_PONTO = "idPonto";
    public static final String ARG_NOME_PONTO = "nomePonto"; // usado no label "{nomePonto}" do grafo

    private static final int TAMANHO_FOTO_AMPLIADA = 1600; // px

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_fotos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvNome = view.findViewById(R.id.tvNomePonto);
        TextView tvInfo = view.findViewById(R.id.tvInfoPonto);
        TextView tvQuantidade = view.findViewById(R.id.tvQuantidadeFotos);
        View painelVazio = view.findViewById(R.id.painelSemFotos);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewFotos);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        FotoAdapter adapter = new FotoAdapter(this::mostrarFotoAmpliada);
        recyclerView.setAdapter(adapter);

        long idPonto = requireArguments().getLong(ARG_ID_PONTO);
        FotosViewModel viewModel = new ViewModelProvider(this).get(FotosViewModel.class);
        viewModel.carregar(idPonto);

        viewModel.getPonto().observe(getViewLifecycleOwner(), ponto -> {
            if (ponto == null) return; // dados ainda carregando

            tvNome.setText(ponto.getNome());
            tvInfo.setText(getString(R.string.fotos_info_ponto,
                    ponto.getRaio(), ponto.getLatitude(), ponto.getLongitude()));

            int quantidade = ponto.getFotos().size();
            tvQuantidade.setText(getResources().getQuantityString(R.plurals.fotos_quantidade,
                    quantidade, quantidade));

            painelVazio.setVisibility(quantidade == 0 ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(quantidade == 0 ? View.GONE : View.VISIBLE);
            adapter.atualizar(ponto.getFotos());
        });

        view.findViewById(R.id.btIrParaCamera).setOnClickListener(v -> {
            if (NavHostFragment.findNavController(this).getCurrentDestination() != null
                    && NavHostFragment.findNavController(this).getCurrentDestination().getId() == R.id.fotosFragment)
                NavHostFragment.findNavController(this).navigate(R.id.action_fotos_para_camera);
        });
    }

    // Abre a foto em tamanho maior, com data e coordenadas de onde foi tirada
    private void mostrarFotoAmpliada(Foto foto) {
        ImageView imageView = new ImageView(requireContext());
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(DateFormat.format("dd/MM/yyyy HH:mm", foto.getDataHora()))
                .setMessage(getString(R.string.foto_coordenadas, foto.getLatitude(), foto.getLongitude()))
                .setView(imageView)
                .setPositiveButton(R.string.fechar, null)
                .show();

        // a foto é carregada fora da thread principal
        Handler threadPrincipal = new Handler(Looper.getMainLooper());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            Bitmap imagem = ImagemUtil.carregar(foto.getCaminho(), TAMANHO_FOTO_AMPLIADA);
            threadPrincipal.post(() -> imageView.setImageBitmap(imagem));
        });
        executor.shutdown(); // termina a thread assim que a foto for carregada
    }
}
