package irs;

import java.util.*;

public class IRS {

	private int yearr;
	private List<Salary> salaryList;
	private Map<Integer, Set<Salary>> employerSalarySet = new HashMap<>();
    private Map<Integer, Map<Integer, Salary>> employerEmployeeSalaryMap = new HashMap<>();
    private List<Map<Integer, Salary>> employerSalaryListMap = new ArrayList<>();
    private List<Set<Salary>> employerSalaryListSet = new ArrayList<>();
    private List<Integer>[] employerArrayList = null;
    private Map<Integer, Employee>[] employerArrayMap = null;

	public int getYear() {
		return yearr;
	}

	public void setYear(int yearr) {
		this.yearr = yearr;
	}

	public List<Salary> getSalaryList() {
		return salaryList;
	}

	public void setSalaryList(List<Salary> salaryList) {
		this.salaryList = salaryList;
		for (Salary sal : salaryList) {
			int empId = sal.getEmployerId();
			if (!this.employerSalarySet.containsKey(empId)) {
				this.employerSalarySet.put(empId, new HashSet<Salary>());
			}
			this.employerSalarySet.get(empId).add(sal);
		}
		System.out.println("Employee salary set: "+this.employerSalarySet);
	}

	public Salary getSalaryfromIRS(int employeeId) {
		for(Salary salary : this.salaryList) {
			if (salary.getEmployeeId() == employeeId) {
				return salary;
			}
		}
		return null;
	}

	public Set<Salary> getSalarySet(int employeeId) {
		return this.employerSalarySet.get(employeeId);
	}

	public Map<Integer, Set<Salary>> getAllSalarySets() {
		return this.employerSalarySet;
	}

	public void setAllSalarySets(Map<Integer, Set<Salary>> employerSalarySet) {
		this.employerSalarySet = employerSalarySet;
	}

    public void setAllSalaryMaps(Map<Integer, Map<Integer, Salary>> employerSalaryMap) {
        this.employerEmployeeSalaryMap = employerSalaryMap;
    }

    public void setEmployerSalaryListMap(List<Map<Integer, Salary>> employerSalaryListMap) {
        this.employerSalaryListMap = employerSalaryListMap;
    }

    public void setEmployerSalaryListSet(List<Set<Salary>> employerSalaryListSet) {
        this.employerSalaryListSet = employerSalaryListSet;
    }

    public void setEmployerArrayList(List<Integer>[] employerArrayList) {
        this.employerArrayList = employerArrayList;
    }

    public void setEmployerArrayMap(Map<Integer, Employee>[] employerArrayMap) {
        this.employerArrayMap = employerArrayMap;
    }
    }
