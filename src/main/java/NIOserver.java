import org.apache.commons.io.FilenameUtils;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

public class NIOserver {
    private String dirActual;
    private String raiz;

    public NIOserver() {
        try{
            File f = new File("");
            String ruta = f.getAbsolutePath();
            String carpeta="drive";
            raiz = ruta+"\\"+carpeta+"\\";
            dirActual = raiz;
            System.out.println("ruta:"+raiz);
            File f2 = new File(raiz);
            f2.mkdirs();
            f2.setWritable(true);

            //* prof

            //ByteBuffer buffer = ByteBuffer.allocate(10000);
            Selector selector = Selector.open();
            ServerSocketChannel server = ServerSocketChannel.open();
            server.configureBlocking(false);
            server.setOption(StandardSocketOptions.SO_REUSEADDR,true);
            server.socket().bind(new InetSocketAddress("127.0.0.1", 3500));
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Servidor iniciado esperando por archivos...");

            // prof */

            for(;;){


                System.out.println("Entra for");

                int x=selector.select(5000);
                //System.out.println("Selector devuelve: "+x);

                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

                while (iterator.hasNext()) {
                    System.out.println("Entra while");

                    SelectionKey key = (SelectionKey) iterator.next();
                    iterator.remove();

                    if (key.isAcceptable()) {
                        SocketChannel client = server.accept();
                        System.out.println("Cliente conectado desde" +client.socket().getInetAddress( )+ ":" +client.socket( ).getPort( ));
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_WRITE| SelectionKey.OP_READ);

                        continue;
                    }

                    SocketChannel channel = (SocketChannel) key.channel();

                    if(key.isWritable()){
                        mostrarArchivos(channel);
                        key.interestOps(SelectionKey.OP_READ);
                        System.out.println("write");
                        continue;
                    }
                    else if(key.isReadable()){
                        System.out.println("read");
                        //while (true){//bucle de escucha de instrucciones
                            ByteBuffer bb = ByteBuffer.allocate(4);
                            bb.clear();
                            channel.read(bb);
                            bb.flip();
                            int elec = bb.getInt();
                            System.out.println("elec = " + elec);

                            switch (elec) {
                                case 1 -> {   //subir archivo o carpeta
                                    subir(channel,selector);
                                    //mostrarArchivos(channel);
                                }
                                /*case 2 -> {
                                    System.out.println("El cliente quiere descargar");
                                }
                                case 3 -> {
                                    eliminar();
                                    mostrarArchivos();
                                }
                                case 4 -> {
                                    System.out.println("cambiar dir");
                                    cambiarDir();
                                }*/
                            }
                        //}
                    }
                }
                //prof


            }//for

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private void escribeObjeto(Object o, SocketChannel channel) throws IOException {    //Envía un objeto al canal
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos2 = new ObjectOutputStream(baos);
        oos2.writeObject(o);
        oos2.flush();
        byte[] b = baos.toByteArray();
        ByteBuffer buffer = ByteBuffer.wrap(b);
        channel.write(buffer);
        System.out.println("Objeto enviado");
        oos2.close();
        baos.close();
    }
    private Object leeObjeto(SocketChannel client) throws IOException, ClassNotFoundException {
        ByteBuffer b = ByteBuffer.allocate(2000);
        b.clear();
        client.read(b);
        b.flip();
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(b.array()));
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    public void mostrarArchivos(SocketChannel channel) throws IOException, InterruptedException {
        File f = new File(dirActual);
        File []listaDeArchivos = f.listFiles();

        escribeObjeto(listaDeArchivos,channel);
    }


    public void subir(SocketChannel channel, Selector selector) throws IOException, ClassNotFoundException {
        selector.select();
        File f = (File)leeObjeto(channel); //archivo que recibo

        String fileName = f.toString();
        String aux = FilenameUtils.getExtension(fileName);

        if(aux.equals("zip")) {
            System.out.println("\nEl cliente quiere subir carpeta");
            subirDir(f,channel);
        }
        else {
            System.out.println("\nEl cliente quiere subir un archivo");
            subirArchivo(f,channel);
        }
    }

    public void subirDir(File f, SocketChannel channel) throws IOException{

        System.out.println("carpeta a subir:"+f.getName());
        String destino = Paths.get(dirActual, f.getName()).toString();

        subirArchivo(f,channel);

        Path descom = Paths.get(dirActual, FilenameUtils.removeExtension(f.getName()) );

        System.out.println("Descomprimiendo " + destino + " en " + descom.toString());

        new net.lingala.zip4j.ZipFile(destino).extractAll(descom.toString());
        new File(destino).delete();
    }

    public void subirArchivo(File f, SocketChannel channel) throws IOException {
        long tam = f.length();

        System.out.println("Comienza descarga del archivo '"+f.getName()+"' de "+tam/1024+" kb");
        System.out.println("Subiendo a "+dirActual);
        DataOutputStream dosf = new DataOutputStream(new FileOutputStream(dirActual+f.getName()));

        long recibidos=0;
        int l;
        while(recibidos<tam){
            ByteBuffer b = ByteBuffer.allocate(1500);
            b.clear();
            channel.read(b);
            b.flip();
            /*byte[] b = new byte[1500];
            l = ois.read(b,0,b.length);*/
            l = b.limit();

            dosf.write(b.array(),0,l);
            dosf.flush();
            recibidos += l;
        }//while

        System.out.println("Archivo recibido");
        dosf.close();
    }


    public static void main(String[] args) {
        new NIOserver();
    }
}
