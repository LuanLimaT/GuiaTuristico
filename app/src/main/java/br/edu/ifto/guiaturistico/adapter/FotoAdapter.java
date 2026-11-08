package br.edu.ifto.guiaturistico.adapter;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.edu.ifto.guiaturistico.R;
import br.edu.ifto.guiaturistico.model.Foto;
import br.edu.ifto.guiaturistico.util.ImagemUtil;


// Mostra as fotos de um ponto turístico em grade.
//As miniaturas são carregadas em segundo plano e guardadas num cache,
//assim a rolagem da lista não trava.

public class FotoAdapter extends RecyclerView.Adapter<FotoAdapter.FotoViewHolder> {

    private static final int TAMANHO_MINIATURA = 400; // px

    public interface OnFotoClickListener {
        void onFotoClick(Foto foto);
    }

    private final List<Foto> listaFotos = new ArrayList<>();
    private final OnFotoClickListener listener;

    // static: as mesmas 2 threads e o mesmo cache servem para todas as vezes que a tela abrir
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static final Handler threadPrincipal = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String chave, Bitmap imagem) {
            return imagem.getByteCount();
        }
    };

    public FotoAdapter(OnFotoClickListener listener) {
        this.listener = listener;
    }

    // Recebe as fotos e mostra as mais recentes primeiro.
    public void atualizar(List<Foto> fotos) {
        listaFotos.clear();
        for (int i = fotos.size() - 1; i >= 0; i--)
            listaFotos.add(fotos.get(i));
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_foto, parent, false);
        return new FotoViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull FotoViewHolder holder, int position) {
        Foto foto = listaFotos.get(position);
        holder.tvData.setText(DateFormat.format("dd/MM/yyyy HH:mm", foto.getDataHora()));
        holder.itemView.setOnClickListener(v -> listener.onFotoClick(foto));

        // marca qual foto este ViewHolder deve mostrar (ele é reaproveitado na rolagem)
        String caminho = foto.getCaminho();
        holder.ivFoto.setTag(caminho);

        Bitmap emCache = cache.get(caminho);
        if (emCache != null) {
            holder.ivFoto.setImageBitmap(emCache);
            return;
        }

        holder.ivFoto.setImageDrawable(null);
        executor.execute(() -> {
            Bitmap miniatura = ImagemUtil.carregar(caminho, TAMANHO_MINIATURA);
            if (miniatura == null) return;
            cache.put(caminho, miniatura);
            threadPrincipal.post(() -> {
                // só mostra se o ViewHolder ainda estiver exibindo a mesma foto
                if (caminho.equals(holder.ivFoto.getTag()))
                    holder.ivFoto.setImageBitmap(miniatura);
            });
        });
    }

    @Override
    public int getItemCount() {
        return listaFotos.size();
    }

    public static class FotoViewHolder extends RecyclerView.ViewHolder {
        ImageView ivFoto;
        TextView tvData;

        public FotoViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFoto = itemView.findViewById(R.id.ivFoto);
            tvData = itemView.findViewById(R.id.tvDataFoto);
        }
    }
}
