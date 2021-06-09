import org.apache.commons.io.FilenameUtils;
import org.zeroturnaround.zip.ZipUtil;
import javax.swing.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Scanner;

public class NIOclient {
    private String dirActual = "drive";
    private Selector selector;
    private String ruta;

    public NIOclient() {
        try {
            selector = Selector.open();
            SocketChannel client = SocketChannel.open();
            client.configureBlocking(false);
            client.connect(new InetSocketAddress("127.0.0.1", 3500));
            client.register(selector, SelectionKey.OP_CONNECT);

            while (true) {

                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

                while (iterator.hasNext()) {

                    SelectionKey key = (SelectionKey) iterator.next();
                    iterator.remove();

                    if (key.isConnectable()) {
                        if (client.isConnectionPending()) {
                            System.out.println("Intentando establecer la conexion\n");
                            try {
                                client.finishConnect();
                                System.out.println("Conexion establecida ;)\n");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        }
                        client.register(selector, SelectionKey.OP_READ| SelectionKey.OP_WRITE);
                        continue;
                    }

                    if (key.isReadable()) {
                        mostrarArchivos(client);
                        key.interestOps(SelectionKey.OP_WRITE);
                        continue;
                    }

                    else if (key.isWritable()){
                        int elec;
                        Scanner reader = new Scanner(System.in);
                        do{
                            System.out.println("\nMenu\n1-Subir un archivo o carpeta\n2-Descargar\n3-Eliminar un archivo o carpeta\n4-Cambiar directorio\n0-Salir\n\n>");
                            elec= reader.nextInt();
                            ByteBuffer bb = ByteBuffer.allocate(4).putInt(elec);
                            bb.flip();
                            client.write(bb);

                            switch (elec) {
                                case 1 -> {   //subir archivo o carpeta
                                    System.out.println("Lanzando JFileChooser...");
                                    subir(client);
                                    mostrarArchivos(client);
                                }
                                /*case 2 -> {   //descargar un archivo o carpeta
                                    System.out.println("Descargar un archivo o carpeta");
                                    descargar();
                                }
                                case 3 -> {   //eliminar archivo o carpeta
                                    System.out.println("Eliminar");
                                    eliminar();
                                    mostrarArchivos();
                                }
                                case 4 -> cambiarDir();   //cambiar directorio*/
                            }
                        }while(elec!=0);
                        System.out.println("Saliendo...");
                        client.close();
                        selector.close();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }//catch
    }

    private void escribeObjeto(Object o, SocketChannel client) throws IOException {    //Envía un objeto al canal
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos2 = new ObjectOutputStream(baos);
        oos2.writeObject(o);
        oos2.flush();
        byte[] b = baos.toByteArray();
        ByteBuffer buffer = ByteBuffer.wrap(b);
        client.write(buffer);
        System.out.println("Objeto enviado");
        oos2.close();
        baos.close();
    }
    private Object leeObjeto(SocketChannel client) throws IOException, ClassNotFoundException {
        ByteBuffer b = ByteBuffer.allocate(20000);
        b.clear();
        client.read(b);
        b.flip();
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(b.array()));
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    public void esperaParaLeer() throws IOException {
        while (true){
            int x = selector.select();
            System.out.println("llaves actualizadas= " + x);
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

            while (iterator.hasNext()) {
                SelectionKey key = (SelectionKey) iterator.next();
                iterator.remove();
                if (key.isWritable()) {
                    System.out.println("cambiando a espera para leer");
                    key.interestOps(SelectionKey.OP_READ);
                }
                if( key.isReadable()) {
                    System.out.println("Listo para leer");
                    //key.interestOps(SelectionKey.OP_WRITE);
                    return;
                }
            }
        }
    }

    public void mostrarArchivos(SocketChannel client) throws IOException, ClassNotFoundException {

        /*ByteBuffer b = ByteBuffer.allocate(10000);
        b.clear();
        client.read(b);
        b.flip();
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(b.array()));*/


        File[] listaDeArchivos = (File[]) leeObjeto(client);
        if (listaDeArchivos == null || listaDeArchivos.length == 0)
            System.out.println("Directorio vacio");
        else {
            System.out.print("\n**** Archivos en " + dirActual + " ****" + "\n\n");
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            for (File archivo : listaDeArchivos) {
                if (archivo.isDirectory())
                    System.out.printf("- (dir) %s  -- %s%n",
                            archivo.getName(),
                            sdf.format(archivo.lastModified())
                    );
            }
            for (File archivo : listaDeArchivos) {
                if (!archivo.isDirectory())
                    System.out.printf("- (file) %s -- %d kb -- %s%n",
                            archivo.getName(),
                            archivo.length() / 1024,
                            sdf.format(archivo.lastModified())
                    );
            }
        }
    }


    public void subir(SocketChannel client){
        try {
            JFileChooser jf = new JFileChooser();
            jf.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);  //para integrar en una sola opcion del menú el subir archivos y carpetas
            int r = jf.showOpenDialog(null);
            if(r==JFileChooser.APPROVE_OPTION){
                File f = jf.getSelectedFile();

                if(f.isDirectory()){
                    String nomzip = f.getAbsolutePath()+".zip";
                    ZipUtil.pack(f, new File(nomzip));
                    subirArchivo(new File(nomzip) , client);
                    new File(nomzip).delete();
                }
                else subirArchivo(f, client);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void subirArchivo(File f, SocketChannel client) throws IOException, ClassNotFoundException {
        long tam = f.length();
        System.out.println("Enviando '"+f.getName()+"' de "+tam/1024+" kb");
        escribeObjeto(f,client);

        DataInputStream disf = new DataInputStream(new FileInputStream(f.getAbsolutePath()));
        long enviados = 0;
        int l;
        while (enviados<tam){
            byte[] b = new byte[1500];
            l=disf.read(b);
            ByteBuffer buffer = ByteBuffer.wrap(b);
            client.write(buffer);
            //bloquearse hasta que el server haya leido
            esperaParaLeer();//selector.select();
            leeObjeto(client);
            enviados += l;
        }
        disf.close();
        System.out.println("Archivo enviado");
    }

    public static void main(String[] args) {
        new NIOclient();
    }
}
