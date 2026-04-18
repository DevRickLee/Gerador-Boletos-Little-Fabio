import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.Scanner;

public class Main {
    // Formato de data aceito na entrada e exibido na saida.
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    // Data base usada no fator de vencimento do boleto.
    private static final LocalDate BASE_FATOR_VENCIMENTO = LocalDate.of(1997, 10, 7);
    // Gerador unico para numeros aleatorios do programa.
    private static final Random RANDOM = new Random();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Gerador de Boleto de Teste ===");
        System.out.println("Bancos disponiveis: 260 (Nubank), 237 (Bradesco), 341 (Itau)");

        // Coleta os dados principais para montar o boleto.
        Banco banco = lerBanco(scanner);
        BigDecimal valor = lerValor(scanner);
        LocalDate vencimento = lerData(scanner);

        // Gera um identificador de cliente propositalmente invalido para testes.
        String codigoClienteInvalido = gerarCodigoClienteInvalido();
        Boleto boleto = gerarBoletoTeste(banco, valor, vencimento, codigoClienteInvalido);

        imprimirBoleto(boleto);
    }

    private static Banco lerBanco(Scanner scanner) {
        // Aceita apenas os bancos suportados.
        while (true) {
            System.out.print("Informe o codigo do banco (260/237/341): ");
            String entrada = scanner.nextLine().trim();

            Banco banco = Banco.porCodigo(entrada);
            if (banco != null) {
                return banco;
            }

            System.out.println("Codigo invalido. Use 260, 237 ou 341.");
        }
    }

    private static BigDecimal lerValor(Scanner scanner) {
        // Le o valor com duas casas e valida o intervalo permitido.
        while (true) {
            System.out.print("Informe o valor do boleto (ex: 150.75): ");
            String entrada = scanner.nextLine().trim().replace(',', '.');
            try {
                BigDecimal valor = new BigDecimal(entrada).setScale(2, RoundingMode.HALF_UP);
                if (valor.compareTo(BigDecimal.ZERO) < 0 || valor.compareTo(new BigDecimal("99999999.99")) > 0) {
                    System.out.println("Valor fora da faixa permitida (0.00 a 99999999.99).");
                    continue;
                }
                return valor;
            } catch (NumberFormatException ex) {
                System.out.println("Valor invalido. Tente novamente.");
            }
        }
    }

    private static LocalDate lerData(Scanner scanner) {
        // Le o vencimento no formato dd-MM-yyyy.
        while (true) {
            System.out.print("Informe o vencimento (dd-MM-yyyy): ");
            String entrada = scanner.nextLine().trim();
            try {
                return LocalDate.parse(entrada, DATE_FORMAT);
            } catch (Exception ex) {
                System.out.println("Data invalida. Use o formato dd-MM-yyyy.");
            }
        }
    }

    private static String gerarCodigoClienteInvalido() {
        // Calcula um DV correto e depois altera para forcar invalidacao.
        String base = gerarDigitosAleatorios(9);
        int dvCorreto = modulo11(base);
        int dvInvalido = (dvCorreto + 1) % 10;
        return base + dvInvalido;
    }

    private static Boleto gerarBoletoTeste(Banco banco, BigDecimal valor, LocalDate vencimento, String codigoClienteInvalido) {
        // Monta as partes do codigo de barras e calcula o DV geral.
        String moeda = "9";
        String fatorVencimento = calcularFatorVencimento(vencimento);
        String valorFormatado = formatarValor(valor);
        String campoLivre = montarCampoLivre(banco, codigoClienteInvalido, valor);

        String semDvGeral = banco.codigo + moeda + fatorVencimento + valorFormatado + campoLivre;
        int dvGeral = calcularDvGeralModulo11(semDvGeral);
        String codigoBarras = banco.codigo + moeda + dvGeral + fatorVencimento + valorFormatado + campoLivre;
        String linhaDigitavel = montarLinhaDigitavel(codigoBarras);

        return new Boleto(banco.nome, banco.codigo, codigoClienteInvalido, vencimento, valor, codigoBarras, linhaDigitavel);
    }

    private static String montarCampoLivre(Banco banco, String codigoClienteInvalido, BigDecimal valor) {
        // Campo livre didatico de 25 posicoes, variando por banco.
        String nossoNumero = gerarDigitosAleatorios(11);
        String valorCompacto4 = formatarValor(valor).substring(6); // ultimos 4 digitos do valor em centavos

        switch (banco) {
            case NUBANK:
                // Layout didatico de 25 posicoes para teste
                return padLeft(codigoClienteInvalido, 10)
                        + "01"
                        + nossoNumero
                        + "26"
                        + valorCompacto4.substring(0, 2);
            case BRADESCO:
                return "09"
                        + nossoNumero
                        + padLeft(codigoClienteInvalido.substring(0, 4), 4)
                        + padLeft(codigoClienteInvalido.substring(4, 10), 6)
                        + "2"
                        + valorCompacto4.substring(0, 2);
            case ITAU:
                return "109"
                        + nossoNumero.substring(0, 8)
                        + padLeft(codigoClienteInvalido.substring(0, 4), 4)
                        + padLeft(codigoClienteInvalido.substring(4, 9), 5)
                        + codigoClienteInvalido.substring(9)
                        + "341"
                        + valorCompacto4.substring(0, 3);
            default:
                throw new IllegalArgumentException("Banco nao suportado.");
        }
    }

    private static String calcularFatorVencimento(LocalDate vencimento) {
        // Diferenca em dias desde a data base, limitada a 4 digitos.
        long dias = ChronoUnit.DAYS.between(BASE_FATOR_VENCIMENTO, vencimento);
        if (dias < 0) {
            return "0000";
        }
        return String.format("%04d", dias % 10000);
    }

    private static String formatarValor(BigDecimal valor) {
        // Converte para centavos e preenche com zeros a esquerda (10 digitos).
        BigDecimal centavos = valor.multiply(new BigDecimal("100"));
        long valorLong = centavos.longValueExact();
        return String.format("%010d", valorLong);
    }

    private static String montarLinhaDigitavel(String codigoBarras) {
        // Quebra o codigo de barras nos 5 campos da linha digitavel.
        String campo1 = codigoBarras.substring(0, 4) + codigoBarras.substring(19, 24);
        String campo2 = codigoBarras.substring(24, 34);
        String campo3 = codigoBarras.substring(34, 44);
        String campo4 = codigoBarras.substring(4, 5);
        String campo5 = codigoBarras.substring(5, 19);

        String campo1ComDv = campo1 + modulo10(campo1);
        String campo2ComDv = campo2 + modulo10(campo2);
        String campo3ComDv = campo3 + modulo10(campo3);

        return formatarCampo(campo1ComDv)
                + " " + formatarCampo(campo2ComDv)
                + " " + formatarCampo(campo3ComDv)
                + " " + campo4
                + " " + campo5;
    }

    private static String formatarCampo(String campo) {
        // Insere ponto para melhorar leitura visual da linha digitavel.
        return campo.substring(0, 5) + "." + campo.substring(5);
    }

    private static int calcularDvGeralModulo11(String numero) {
        // DV geral do codigo de barras seguindo regra modulo 11.
        int soma = 0;
        int peso = 2;

        for (int i = numero.length() - 1; i >= 0; i--) {
            soma += Character.getNumericValue(numero.charAt(i)) * peso;
            peso = (peso == 9) ? 2 : peso + 1;
        }

        int resto = soma % 11;
        int dv = 11 - resto;
        if (dv == 0 || dv == 10 || dv == 11) {
            return 1;
        }
        return dv;
    }

    private static int modulo10(String numero) {
        // DV dos campos 1, 2 e 3 da linha digitavel.
        int soma = 0;
        int peso = 2;

        for (int i = numero.length() - 1; i >= 0; i--) {
            int multiplicacao = Character.getNumericValue(numero.charAt(i)) * peso;
            soma += (multiplicacao > 9) ? (multiplicacao - 9) : multiplicacao;
            peso = (peso == 2) ? 1 : 2;
        }

        int resto = soma % 10;
        return (resto == 0) ? 0 : (10 - resto);
    }

    private static int modulo11(String numero) {
        // Regra auxiliar para gerar DV do codigo de cliente de teste.
        int soma = 0;
        int peso = 2;

        for (int i = numero.length() - 1; i >= 0; i--) {
            soma += Character.getNumericValue(numero.charAt(i)) * peso;
            peso = (peso == 9) ? 2 : peso + 1;
        }

        int resto = soma % 11;
        int dv = 11 - resto;
        if (dv == 10 || dv == 11) {
            return 0;
        }
        return dv;
    }

    private static String gerarDigitosAleatorios(int tamanho) {
        // Gera uma sequencia numerica pseudoaleatoria.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tamanho; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private static String padLeft(String valor, int tamanho) {
        // Completa com zero a esquerda para garantir tamanho fixo.
        if (valor.length() >= tamanho) {
            return valor.substring(0, tamanho);
        }
        return String.format("%" + tamanho + "s", valor).replace(' ', '0');
    }

    private static void imprimirBoleto(Boleto boleto) {
        // Exibe os dados finais do boleto de teste no console.
        System.out.println("\n=== Boleto Gerado (Teste) ===");
        System.out.println("Banco: " + boleto.nomeBanco + " (" + boleto.codigoBanco + ")");
        System.out.println("Codigo do cliente (invalido): " + boleto.codigoClienteInvalido);
        System.out.println("Vencimento: " + boleto.vencimento.format(DATE_FORMAT));
        System.out.println("Valor: R$ " + boleto.valor.setScale(2, RoundingMode.HALF_UP));
        System.out.println("Codigo de barras (44): " + boleto.codigoBarras);
        System.out.println("Linha digitavel (5 campos): " + boleto.linhaDigitavel);
    }

    private static class Boleto {
        // Estrutura simples para transportar os dados do boleto gerado.
        String nomeBanco;
        String codigoBanco;
        String codigoClienteInvalido;
        LocalDate vencimento;
        BigDecimal valor;
        String codigoBarras;
        String linhaDigitavel;

        Boleto(String nomeBanco, String codigoBanco, String codigoClienteInvalido,
               LocalDate vencimento, BigDecimal valor, String codigoBarras, String linhaDigitavel) {
            this.nomeBanco = nomeBanco;
            this.codigoBanco = codigoBanco;
            this.codigoClienteInvalido = codigoClienteInvalido;
            this.vencimento = vencimento;
            this.valor = valor;
            this.codigoBarras = codigoBarras;
            this.linhaDigitavel = linhaDigitavel;
        }
    }

    private enum Banco {
        // Bancos suportados no gerador didatico.
        NUBANK("260", "Nubank"),
        BRADESCO("237", "Bradesco"),
        ITAU("341", "Itau");

        private final String codigo;
        private final String nome;

        Banco(String codigo, String nome) {
            this.codigo = codigo;
            this.nome = nome;
        }

        static Banco porCodigo(String codigo) {
            // Faz a conversao do codigo digitado para o enum correspondente.
            for (Banco banco : values()) {
                if (banco.codigo.equals(codigo)) {
                    return banco;
                }
            }
            return null;
        }
    }
}


